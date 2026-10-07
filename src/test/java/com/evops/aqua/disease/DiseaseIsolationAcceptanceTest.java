package com.evops.aqua.disease;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseChangeEvidence;
import com.evops.aqua.disease.entity.DiseaseFollowupTask;
import com.evops.aqua.disease.entity.DiseaseIsolation;
import com.evops.aqua.disease.entity.DiseaseObservation;
import com.evops.aqua.disease.entity.DiseaseStockMovement;
import com.evops.aqua.disease.mapper.DiseaseChangeEvidenceMapper;
import com.evops.aqua.disease.mapper.DiseaseFollowupTaskMapper;
import com.evops.aqua.disease.mapper.DiseaseIsolationMapper;
import com.evops.aqua.disease.mapper.DiseaseObservationMapper;
import com.evops.aqua.disease.mapper.DiseaseStockMovementMapper;
import com.evops.aqua.disease.service.DiseaseFollowupService;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 疫病观察与隔离池调度验收（aqua-4）。
 * 主线：登记隔离池档案 → 发现异常症状生成观察单（幂等）→ 按风险等级推荐隔离池
 * → 调度失败说明冲突原因（容量/消毒/权限/混养）→ 调度成功逐笔记数量账
 * → 隔离中禁止出池/换池/投苗混养 → 非兽医不得解除 → 兽医复核解除回原池
 * → 定时复查任务重启重跑不多写、操作者不丢 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiseaseIsolationAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private DiseaseObservationMapper observationMapper;
    @Autowired
    private DiseaseIsolationMapper isolationMapper;
    @Autowired
    private DiseaseStockMovementMapper movementMapper;
    @Autowired
    private DiseaseFollowupTaskMapper followupTaskMapper;
    @Autowired
    private DiseaseChangeEvidenceMapper evidenceMapper;
    @Autowired
    private DiseaseFollowupService followupService;
    @Autowired
    private Scheduler quartzScheduler;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondA;      // 原池（东山湾）
    private Long pondIso1;   // 隔离池1（东山湾，容量60000）
    private Long pondIso2;   // 隔离池2（西沙湾，容量40000）
    private Long pondB;      // 普通池（东山湾）
    private Long batch1;
    private Long batch2;
    private Long obs1;
    private Long obs2;
    private Long isolation1;

    @Test
    void acceptanceDiseaseObservationAndIsolation() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建池：原池、两个隔离池（不同海区/容量）、一个普通池", () -> {
            pondA = createPond("DS-A", "疫病原池", "东山湾海区");
            pondIso1 = createPond("DS-ISO1", "隔离池一号", "东山湾海区");
            pondIso2 = createPond("DS-ISO2", "隔离池二号", "西沙湾海区");
            pondB = createPond("DS-B", "普通养成池", "东山湾海区");
        });

        checkpoint("CP2", "登记隔离调度档案：角色/容量/消毒有效期/调入权限；缺操作者被拒", () -> {
            Map<String, Object> noOperator = guardBody(pondIso1, "ISOLATION", 60000, "2026-10-20", true);
            noOperator.remove("operator");
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/disease/pond-guards", noOperator));
            check(!denied.path("success").asBoolean(true), "缺操作者登记档案应被参数校验拒绝");

            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/disease/pond-guards",
                    guardBody(pondIso1, "ISOLATION", 60000, "2026-10-20", true))), "登记隔离池1档案");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/disease/pond-guards",
                    guardBody(pondIso2, "ISOLATION", 40000, "2026-10-20", true))), "登记隔离池2档案");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/disease/pond-guards",
                    guardBody(pondB, "NORMAL", null, null, true))), "登记普通池档案");
        });

        checkpoint("CP3", "投苗批次DS-C01共50000尾入原池", () -> {
            batch1 = stockBatch("DS-C01", pondA, 50000);
            check(batch1 != null, "投苗批次应落库");
        });

        checkpoint("CP4", "发现异常症状生成观察单（高风险）；同requestId重复提交幂等，观察人落库", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    obsBody(batch1, pondA, "HIGH", "体表溃疡、摄食骤降、游动异常", "张巡塘", "obs-1")));
            assertSuccess(resp, "生成观察单");
            obs1 = resp.path("data").path("id").asLong();
            check("OBSERVING".equals(resp.path("data").path("status").asText()), "新观察单应为观察中");
            check("张巡塘".equals(resp.path("data").path("observer").asText()), "观察人必须落库");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    obsBody(batch1, pondA, "HIGH", "体表溃疡、摄食骤降、游动异常", "张巡塘", "obs-1")));
            assertSuccess(again, "重复提交观察单");
            check(again.path("data").path("id").asLong() == obs1, "重复提交应返回同一观察单");
            Long count = observationMapper.selectCount(new QueryWrapper<DiseaseObservation>()
                    .eq("batch_id", batch1));
            check(count != null && count == 1L, "幂等提交不得产生第二条观察单，实际=" + count);
        });

        checkpoint("CP5", "按风险等级推荐隔离池：高风险推荐同海区ISO1，ISO2因容量不足被排除并说明原因", () -> {
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/disease/observations/" + obs1 + "/recommend-pools?dispatchDate=2026-10-06", null));
            assertSuccess(resp, "推荐隔离池");
            JsonNode recommended = resp.path("data").path("recommended");
            check(recommended.size() == 1
                            && recommended.get(0).path("pondId").asLong() == pondIso1
                            && recommended.get(0).path("sameSeaArea").asBoolean(),
                    "高风险应推荐同海区隔离池ISO1，实际=" + recommended);
            JsonNode excluded = resp.path("data").path("excluded");
            check(excluded.size() == 1 && excluded.get(0).path("pondId").asLong() == pondIso2
                            && excluded.get(0).path("reasons").toString().contains("容量不足"),
                    "ISO2容量40000装不下50000尾，应被排除并说明容量不足，实际=" + excluded);
        });

        checkpoint("CP6", "调度失败必须说明冲突原因：容量不足；权限关闭+消毒过期多原因合并；失败留证且不留业务记录", () -> {
            // 容量不足
            JsonNode r1 = json(call(HttpMethod.POST, "/api/aqua/disease/observations/" + obs1 + "/dispatch",
                    dispatchBody(pondIso2, "2026-10-06", "王调度", "disp-fail-1")));
            check(!r1.path("success").asBoolean(true), "容量不足的调度应失败");
            check(r1.path("message").asText().contains("容量不足"),
                    "失败原因应说明容量不足：" + r1.path("message").asText());

            // 关闭ISO1调入权限并令消毒过期：一次失败应同时说明两类冲突
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/disease/pond-guards",
                    guardBody(pondIso1, "ISOLATION", 60000, "2026-10-01", false))), "临时收紧ISO1档案");
            JsonNode r2 = json(call(HttpMethod.POST, "/api/aqua/disease/observations/" + obs1 + "/dispatch",
                    dispatchBody(pondIso1, "2026-10-06", "王调度", "disp-fail-2")));
            check(!r2.path("success").asBoolean(true), "权限关闭且消毒过期的调度应失败");
            check(r2.path("message").asText().contains("调入权限")
                            && r2.path("message").asText().contains("消毒"),
                    "失败原因应同时说明权限与消毒冲突：" + r2.path("message").asText());
            // 恢复ISO1档案
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/disease/pond-guards",
                    guardBody(pondIso1, "ISOLATION", 60000, "2026-10-20", true))), "恢复ISO1档案");

            check(isolationMapper.selectCount(new QueryWrapper<>()) == 0L, "调度失败不得残留隔离单");
            check(movementMapper.selectCount(new QueryWrapper<>()) == 0L, "调度失败不得残留数量账");
            List<DiseaseChangeEvidence> failed = evidenceMapper.selectList(
                    new QueryWrapper<DiseaseChangeEvidence>()
                            .eq("action", DiseaseChangeEvidence.ACTION_DISPATCH_FAILED));
            check(failed.size() == 2, "两次调度失败应各留一条失败证据，实际=" + failed.size());
            check(failed.stream().allMatch(e -> "王调度".equals(e.getOperator())
                            && e.getDetail().contains("冲突原因")),
                    "失败证据必须带操作者与冲突原因");
        });

        checkpoint("CP7", "调度成功：生成隔离单与调入数量账，批次入隔离池，观察单转隔离中；重复调度幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/disease/observations/" + obs1 + "/dispatch",
                    dispatchBody(pondIso1, "2026-10-06", "王调度", "disp-1")));
            assertSuccess(resp, "调度到ISO1");
            isolation1 = resp.path("data").path("id").asLong();
            check("ISOLATED".equals(resp.path("data").path("status").asText()), "隔离单应为隔离中");
            check(resp.path("data").path("quantity").asInt() == 50000
                            && "王调度".equals(resp.path("data").path("isolatedBy").asText()),
                    "隔离单数量与调度人必须落库");
            check(batchMapper.selectById(batch1).getPondId().equals(pondIso1), "批次应已调入隔离池");
            check(DiseaseObservation.STATUS_ISOLATED.equals(
                    observationMapper.selectById(obs1).getStatus()), "观察单应转为已隔离");

            List<DiseaseStockMovement> moves = movementMapper.selectList(
                    new QueryWrapper<DiseaseStockMovement>().eq("batch_id", batch1));
            check(moves.size() == 1
                            && DiseaseStockMovement.TYPE_ISOLATE_IN.equals(moves.get(0).getMoveType())
                            && moves.get(0).getFromPondId().equals(pondA)
                            && moves.get(0).getToPondId().equals(pondIso1)
                            && moves.get(0).getQuantity() == 50000
                            && "王调度".equals(moves.get(0).getOperator()),
                    "调入数量账应逐笔落库且带操作者，实际=" + moves.size());

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/disease/observations/" + obs1 + "/dispatch",
                    dispatchBody(pondIso1, "2026-10-06", "王调度", "disp-1")));
            assertSuccess(again, "重复调度请求");
            check(again.path("data").path("id").asLong() == isolation1, "重复调度应返回同一隔离单");
            check(movementMapper.selectCount(new QueryWrapper<DiseaseStockMovement>()
                    .eq("batch_id", batch1)) == 1L, "重复调度不得多写数量账");

            JsonNode trace = json(call(HttpMethod.GET, "/api/aqua/batches/" + batch1 + "/trace", null));
            check(trace.path("data").path("transfers").toString().contains("疫病隔离调度"),
                    "台账追溯应包含隔离调度转移记录");
        });

        checkpoint("CP8", "隔离中的批次禁止出池", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("harvestDate", "2026-10-07");
            body.put("quantity", 50000);
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/harvest", body));
            check(!resp.path("success").asBoolean(true), "隔离中批次出池应被拒绝");
            check(resp.path("message").asText().contains("隔离"),
                    "拒绝原因应说明处于隔离中：" + resp.path("message").asText());
            check(SeedlingBatch.STATUS_STOCKED.equals(batchMapper.selectById(batch1).getStatus()),
                    "批次状态应保持不变");
        });

        checkpoint("CP9", "隔离中的批次禁止混养：禁止换池、隔离池禁止投苗、他批不得调入同池", () -> {
            Map<String, Object> transfer = new HashMap<>();
            transfer.put("toPondId", pondB);
            transfer.put("transferDate", "2026-10-07");
            transfer.put("reason", "尝试换池");
            JsonNode r1 = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/transfer", transfer));
            check(!r1.path("success").asBoolean(true) && r1.path("message").asText().contains("隔离"),
                    "隔离中批次换池应被拒绝：" + r1.path("message").asText());

            JsonNode r2 = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("DS-ILLEGAL", pondIso1, 1000)));
            check(!r2.path("success").asBoolean(true) && r2.path("message").asText().contains("混养"),
                    "隔离池在隔离期间投苗应被拒绝并说明禁止混养：" + r2.path("message").asText());

            // 另一批次观察单调度到同一隔离池：混养冲突必须说明
            batch2 = stockBatch("DS-C02", pondB, 50000);
            JsonNode obs2Resp = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    obsBody(batch2, pondB, "MEDIUM", "零星死亡、离群独游", "李巡塘", "obs-2")));
            assertSuccess(obs2Resp, "生成第二观察单");
            obs2 = obs2Resp.path("data").path("id").asLong();
            JsonNode r3 = json(call(HttpMethod.POST, "/api/aqua/disease/observations/" + obs2 + "/dispatch",
                    dispatchBody(pondIso1, "2026-10-06", "王调度", "disp-fail-3")));
            check(!r3.path("success").asBoolean(true) && r3.path("message").asText().contains("混养"),
                    "调入已有在隔离批次的池应失败并说明混养冲突：" + r3.path("message").asText());

            // 中风险推荐：异海区隔离池应被排除并说明海区原因
            JsonNode rec = json(call(HttpMethod.GET,
                    "/api/aqua/disease/observations/" + obs2 + "/recommend-pools?dispatchDate=2026-10-06", null));
            assertSuccess(rec, "中风险推荐");
            check(rec.path("data").path("excluded").toString().contains("海区"),
                    "中风险应排除异海区隔离池并说明原因：" + rec.path("data").path("excluded"));
        });

        checkpoint("CP10", "解除隔离必须由兽医复核：缺兽医或缺复核意见一律拒绝", () -> {
            JsonNode r1 = json(call(HttpMethod.POST, "/api/aqua/disease/isolations/" + isolation1 + "/release",
                    releaseBody(null, "症状消退，同意解除", "rel-x1")));
            check(!r1.path("success").asBoolean(true), "缺复核兽医应被拒绝");
            JsonNode r2 = json(call(HttpMethod.POST, "/api/aqua/disease/isolations/" + isolation1 + "/release",
                    releaseBody("陈兽医", null, "rel-x2")));
            check(!r2.path("success").asBoolean(true), "缺复核意见应被拒绝");
            check(DiseaseIsolation.STATUS_ISOLATED.equals(isolationMapper.selectById(isolation1).getStatus()),
                    "未复核前隔离单应保持隔离中");
            check(movementMapper.selectCount(new QueryWrapper<DiseaseStockMovement>()
                    .eq("batch_id", batch1)) == 1L, "未复核解除不得写数量账");
        });

        checkpoint("CP11", "兽医复核解除隔离：批次回原池并逐笔记账；重复解除幂等不多写", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/disease/isolations/" + isolation1 + "/release",
                    releaseBody("陈兽医", "连续三日症状消退，抽检正常，同意解除隔离", "rel-1")));
            assertSuccess(resp, "兽医复核解除隔离");
            check("RELEASED".equals(resp.path("data").path("status").asText()), "隔离单应转为已解除");
            check("陈兽医".equals(resp.path("data").path("vetName").asText())
                            && resp.path("data").path("vetReviewRemark").asText().contains("症状消退"),
                    "复核兽医与复核意见必须落库");
            check(batchMapper.selectById(batch1).getPondId().equals(pondA), "批次应回到原池");
            check(DiseaseObservation.STATUS_RELEASED.equals(
                    observationMapper.selectById(obs1).getStatus()), "观察单应转为已解除");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/disease/isolations/" + isolation1 + "/release",
                    releaseBody("陈兽医", "连续三日症状消退，抽检正常，同意解除隔离", "rel-1")));
            assertSuccess(again, "重复解除请求");
            check(movementMapper.selectCount(new QueryWrapper<DiseaseStockMovement>()
                    .eq("batch_id", batch1)) == 2L, "重复解除不得多写数量账");
        });

        checkpoint("CP12", "原池与隔离池之间的数量变化逐笔可查：调入一笔、回池一笔，操作者随行", () -> {
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/disease/isolations/movements?batchId=" + batch1, null));
            assertSuccess(resp, "查询数量账");
            JsonNode moves = resp.path("data");
            check(moves.size() == 2, "数量账应恰为两笔，实际=" + moves.size());
            JsonNode in = moves.get(0);
            JsonNode back = moves.get(1);
            check("ISOLATE_IN".equals(in.path("moveType").asText())
                            && in.path("fromPondId").asLong() == pondA
                            && in.path("toPondId").asLong() == pondIso1
                            && in.path("quantity").asInt() == 50000
                            && "王调度".equals(in.path("operator").asText()),
                    "调入账应为原池->隔离池50000尾，操作者王调度");
            check("RELEASE_BACK".equals(back.path("moveType").asText())
                            && back.path("fromPondId").asLong() == pondIso1
                            && back.path("toPondId").asLong() == pondA
                            && back.path("quantity").asInt() == 50000
                            && "陈兽医".equals(back.path("operator").asText()),
                    "回池账应为隔离池->原池50000尾，操作者陈兽医");
        });

        checkpoint("CP13", "定时复查任务：重启重跑不多写记录，操作者不丢", () -> {
            check(quartzScheduler.getJobDetail(JobKey.jobKey("diseaseFollowupScanJob", "aqua-disease")) != null,
                    "定时任务diseaseFollowupScanJob应已注册到调度器");

            // 首次扫描：仅未结观察单（obs2观察中）生成复查任务；obs1已解除不生成
            Map<String, Object> first = followupService.generateDailyTasks(LocalDate.of(2026, 10, 8));
            check((Integer) first.get("createdCount") == 1, "首次扫描应生成1条复查任务，实际=" + first.get("createdCount"));
            List<DiseaseFollowupTask> tasks = followupTaskMapper.selectList(new QueryWrapper<>());
            check(tasks.size() == 1, "复查任务总数应为1，实际=" + tasks.size());
            DiseaseFollowupTask task = tasks.get(0);
            check(task.getObservationId().equals(obs2)
                            && DiseaseFollowupTask.SCHEDULER_OPERATOR.equals(task.getOperator())
                            && "李巡塘".equals(task.getSourceObserver()),
                    "复查任务必须带调度系统操作者并保留原观察人");

            // 模拟应用重启后定时任务重跑：同日再扫描不得多写
            Map<String, Object> rerun = followupService.generateDailyTasks(LocalDate.of(2026, 10, 8));
            check((Integer) rerun.get("createdCount") == 0 && (Integer) rerun.get("existedCount") == 1,
                    "重启后重跑不得多写记录，实际=" + rerun);
            check(followupTaskMapper.selectCount(new QueryWrapper<>()) == 1L, "重跑后复查任务总数应仍为1");

            // 下一日正常生成新任务；全部记录操作者均不丢
            Map<String, Object> nextDay = followupService.generateDailyTasks(LocalDate.of(2026, 10, 9));
            check((Integer) nextDay.get("createdCount") == 1, "次日应再生成1条复查任务");
            List<DiseaseFollowupTask> all = followupTaskMapper.selectList(new QueryWrapper<>());
            check(all.size() == 2, "复查任务总数应为2，实际=" + all.size());
            check(all.stream().allMatch(t -> t.getOperator() != null && !t.getOperator().trim().isEmpty()
                            && t.getSourceObserver() != null && !t.getSourceObserver().trim().isEmpty()),
                    "每条复查任务的操作者与原观察人都不得为空");
        });

        checkpoint("CP14", "全程留证：每类动作证据齐全且均带操作者，可按对象追溯", () -> {
            check(evidenceCount(DiseaseChangeEvidence.ACTION_UPSERT_GUARD, null) >= 4, "档案登记证据缺失");
            check(evidenceCount(DiseaseChangeEvidence.ACTION_CREATE_OBSERVATION, null) == 2, "观察单证据应为2条");
            check(evidenceCount(DiseaseChangeEvidence.ACTION_DISPATCH, isolation1) == 1, "调度成功证据缺失");
            check(evidenceCount(DiseaseChangeEvidence.ACTION_DISPATCH_FAILED, null) == 3, "调度失败证据应为3条");
            check(evidenceCount(DiseaseChangeEvidence.ACTION_RELEASE, isolation1) == 1, "解除证据缺失");
            check(evidenceCount(DiseaseChangeEvidence.ACTION_FOLLOWUP_GENERATE, null) == 2, "复查任务证据应为2条");
            List<DiseaseChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            check(!all.isEmpty()
                            && all.stream().allMatch(e -> e.getOperator() != null
                            && !e.getOperator().trim().isEmpty()),
                    "每条证据都必须带操作者，不得丢操作者");

            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/disease/evidence?entityType=ISOLATION&entityId=" + isolation1, null));
            assertSuccess(resp, "按对象查询证据");
            check(resp.path("data").size() == 2, "隔离单维度应查到调度+解除两条证据，实际="
                    + resp.path("data").size());
        });

        report.add("验收结论：全部检查点通过——异常症状生成观察单、按风险等级推荐隔离池、转移前校验容量/消毒/权限且失败说明冲突原因、隔离中禁止混养与出池、兽医复核解除、数量逐笔记录、定时任务重启不多写且操作者不丢、全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name, String seaArea) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", seaArea);
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockBatch(String batchNo, Long pondId, int quantity) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", stockBody(batchNo, pondId, quantity)));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> stockBody(String batchNo, Long pondId, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", "LARGE_YELLOW_CROAKER");
        body.put("seedSource", "东山湾苗种场");
        body.put("quantity", quantity);
        body.put("stockDate", "2026-10-01");
        body.put("plannedHarvestDate", "2027-03-01");
        return body;
    }

    private Map<String, Object> guardBody(Long pondId, String role, Integer capacity,
                                          String disinfectedUntil, boolean allowed) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        body.put("pondRole", role);
        if (capacity != null) {
            body.put("capacity", capacity);
        }
        if (disinfectedUntil != null) {
            body.put("disinfectedUntil", disinfectedUntil);
        }
        body.put("transferInAllowed", allowed);
        body.put("operator", "管理员赵海");
        return body;
    }

    private Map<String, Object> obsBody(Long batchId, Long pondId, String risk, String symptoms,
                                        String observer, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        body.put("batchId", batchId);
        body.put("observeDate", "2026-10-05");
        body.put("symptoms", symptoms);
        body.put("riskLevel", risk);
        body.put("observer", observer);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> dispatchBody(Long toPondId, String dispatchDate,
                                             String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("toPondId", toPondId);
        body.put("dispatchDate", dispatchDate);
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> releaseBody(String vetName, String reviewRemark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        if (vetName != null) {
            body.put("vetName", vetName);
        }
        if (reviewRemark != null) {
            body.put("reviewRemark", reviewRemark);
        }
        body.put("requestId", requestId);
        return body;
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<DiseaseChangeEvidence> wrapper = new QueryWrapper<DiseaseChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long c = evidenceMapper.selectCount(wrapper);
        return c == null ? 0L : c;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 疫病观察与隔离池调度验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==================================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
