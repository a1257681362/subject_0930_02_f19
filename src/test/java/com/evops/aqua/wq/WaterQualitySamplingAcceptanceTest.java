package com.evops.aqua.wq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.wq.entity.WqBackfill;
import com.evops.aqua.wq.entity.WqChangeEvidence;
import com.evops.aqua.wq.entity.WqHealthPoint;
import com.evops.aqua.wq.entity.WqRuleVersion;
import com.evops.aqua.wq.entity.WqTask;
import com.evops.aqua.wq.entity.WqTaskItem;
import com.evops.aqua.wq.mapper.WqBackfillMapper;
import com.evops.aqua.wq.mapper.WqChangeEvidenceMapper;
import com.evops.aqua.wq.mapper.WqHealthPointMapper;
import com.evops.aqua.wq.mapper.WqRuleVersionMapper;
import com.evops.aqua.wq.mapper.WqTaskItemMapper;
import com.evops.aqua.wq.mapper.WqTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 水质采样计划与缺测补录验收。
 * 主线：发布分阶段规则 → 建计划 → 按池生成任务（重复请求不产生第二条）
 * → 草稿/提交（缺测必须说明原因）→ 补录只追加不覆盖 → 主管审核后才进健康曲线
 * → 新规则版本只作用于未开始任务 → 所有修改保留证据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WaterQualitySamplingAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private WqRuleVersionMapper versionMapper;
    @Autowired
    private WqTaskMapper taskMapper;
    @Autowired
    private WqTaskItemMapper itemMapper;
    @Autowired
    private WqBackfillMapper backfillMapper;
    @Autowired
    private WqHealthPointMapper healthPointMapper;
    @Autowired
    private WqChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId1;
    private Long pondId2;
    private Long versionV1;
    private Long versionV2;
    private Long planId1;
    private Long planId2;
    private Long taskId1;

    @Test
    void acceptanceWaterQualitySamplingAndBackfill() {
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

        checkpoint("CP1", "建两个养殖池", () -> {
            pondId1 = createPond("WQ-P01", "水质1号池");
            pondId2 = createPond("WQ-P02", "水质2号池");
            Pond pond = pondMapper.selectById(pondId1);
            check(pond != null && "水质1号池".equals(pond.getPondName()), "养殖池落库异常");
        });

        checkpoint("CP2", "发布规则版本V1：分养殖阶段配置采样频率与指标范围", () -> {
            Map<String, Object> body = ruleVersionBody("WQ-RULE-V1",
                    stage("NURSERY", "苗种期", 3,
                            indicator("PH", "pH", "", "7.5", "8.5"),
                            indicator("SALINITY", "盐度", "ppt", "25.0", "32.0"),
                            indicator("DO", "溶解氧", "mg/L", "5.0", "9.0")),
                    stage("GROWOUT", "养成期", 7,
                            indicator("PH", "pH", "", "7.6", "8.6"),
                            indicator("DO", "溶解氧", "mg/L", "4.5", "9.0")));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/wq/rule-versions", body));
            assertSuccess(resp, "发布规则版本V1");
            versionV1 = resp.path("data").path("id").asLong();
            WqRuleVersion row = versionMapper.selectById(versionV1);
            check(WqRuleVersion.STATUS_ACTIVE.equals(row.getStatus()), "V1应为生效版本");
        });

        checkpoint("CP3", "规则版本号不能重复", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/wq/rule-versions",
                    ruleVersionBody("WQ-RULE-V1", stage("NURSERY", "苗种期", 3,
                            indicator("PH", "pH", "", "7.5", "8.5")))));
            check(!resp.path("success").asBoolean(true), "重复规则版本号未被拦截");
            Long count = versionMapper.selectCount(
                    new QueryWrapper<WqRuleVersion>().eq("version_no", "WQ-RULE-V1"));
            check(count != null && count == 1L, "库中V1行数应为1，实际=" + count);
        });

        checkpoint("CP4", "创建采样计划并按池生成任务（3天一次×2池×3个采样日=6条）", () -> {
            Map<String, Object> plan = new HashMap<>();
            plan.put("planNo", "WQ-PLAN-001");
            plan.put("planName", "苗种期水质采样计划");
            plan.put("ruleVersionId", versionV1);
            plan.put("stageCode", "NURSERY");
            plan.put("startDate", "2026-10-03");
            plan.put("endDate", "2026-10-09");
            plan.put("pondIds", list(pondId1, pondId2));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/wq/plans", plan));
            assertSuccess(resp, "创建采样计划");
            planId1 = resp.path("data").path("id").asLong();

            Map<String, Object> gen = new HashMap<>();
            gen.put("requestId", "gen-001");
            JsonNode genResp = json(call(HttpMethod.POST,
                    "/api/aqua/wq/plans/" + planId1 + "/tasks/generate", gen));
            assertSuccess(genResp, "按池生成任务");
            check(genResp.path("data").path("createdCount").asInt() == 6,
                    "首次生成任务数应为6，实际=" + genResp.path("data").path("createdCount").asInt());
            taskId1 = genResp.path("data").path("created").get(0).path("id").asLong();

            WqTask task = taskMapper.selectById(taskId1);
            check(task.getRuleVersionId().equals(versionV1), "任务应冻结V1规则版本");
            check(WqTask.STATUS_DRAFT.equals(task.getStatus()), "新任务应为草稿状态");
            List<WqTaskItem> items = itemsOf(taskId1);
            check(items.size() == 3, "每任务应有3个指标快照，实际=" + items.size());
            check(new BigDecimal("7.5").compareTo(items.get(0).getNormalMin()) == 0,
                    "pH正常下限快照应为7.5：" + items.get(0).getNormalMin());
        });

        checkpoint("CP5", "重复生成请求不能产生第二条结果（幂等）", () -> {
            long totalBefore = taskMapper.selectCount(new QueryWrapper<WqTask>().eq("plan_id", planId1));
            Map<String, Object> gen = new HashMap<>();
            gen.put("requestId", "gen-001");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/wq/plans/" + planId1 + "/tasks/generate", gen));
            assertSuccess(resp, "重复生成请求");
            check(resp.path("data").path("createdCount").asInt() == 0,
                    "重复生成新建数应为0，实际=" + resp.path("data").path("createdCount").asInt());
            check(resp.path("data").path("existedCount").asInt() == 6,
                    "重复生成应识别6条已有任务，实际=" + resp.path("data").path("existedCount").asInt());
            long totalAfter = taskMapper.selectCount(new QueryWrapper<WqTask>().eq("plan_id", planId1));
            check(totalAfter == totalBefore, "重复生成后任务行数变化：" + totalBefore + "->" + totalAfter);
        });

        checkpoint("CP6", "采样员可暂存草稿（只录部分指标）", () -> {
            JsonNode resp = json(call(HttpMethod.PUT, "/api/aqua/wq/tasks/" + taskId1 + "/draft",
                    draft(itemInput("PH", "8.1", false, null))));
            assertSuccess(resp, "暂存草稿");
            List<WqTaskItem> items = itemsOf(taskId1);
            WqTaskItem ph = findItem(items, "PH");
            WqTaskItem salinity = findItem(items, "SALINITY");
            check("8.1".equals(ph.getValueText()), "草稿pH未落库：" + ph.getValueText());
            check(salinity.getValueText() == null && salinity.getMissingReason() == null,
                    "草稿允许其他指标暂缺");
        });

        checkpoint("CP7", "提交时缺测项未说明原因必须被拒绝", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/submit", submit(null, "submit-001")));
            check(!resp.path("success").asBoolean(true), "存在缺测项且无原因，提交应被拒绝");
            check(resp.path("message").asText().contains("缺测"),
                    "缺测提示语不符：" + resp.path("message").asText());
            check(WqTask.STATUS_DRAFT.equals(taskMapper.selectById(taskId1).getStatus()),
                    "提交失败后任务应仍是草稿");
        });

        checkpoint("CP8", "补齐测量值与缺测原因后提交成功；重复提交不产生第二条结果", () -> {
            List<Map<String, Object>> inputs = list(
                    itemInput("PH", "8.1", false, null),
                    itemInput("DO", "6.5", false, null),
                    itemInput("SALINITY", null, true, "采样瓶意外破碎，样品作废"));
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/submit", submit(inputs, "submit-001")));
            assertSuccess(resp, "提交采样结果");
            check(WqTask.STATUS_SUBMITTED.equals(resp.path("data").path("status").asText()),
                    "提交后状态应为SUBMITTED");
            long submitEvidence = evidenceCount(WqChangeEvidence.ACTION_SUBMIT, taskId1);
            check(submitEvidence == 1, "首次提交证据应为1条，实际=" + submitEvidence);

            // 先有有效样本，再重复同一提交请求：返回同一任务，不新增任何结果
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/submit", submit(inputs, "submit-001")));
            assertSuccess(again, "重复提交请求");
            check(again.path("data").path("id").asLong() == taskId1, "重复提交返回的任务id不一致");
            check(WqTask.STATUS_SUBMITTED.equals(again.path("data").path("status").asText()),
                    "重复提交后状态应仍为SUBMITTED");
            check(evidenceCount(WqChangeEvidence.ACTION_SUBMIT, taskId1) == 1,
                    "重复提交又产生了一条提交证据");
        });

        checkpoint("CP9", "补录只能追加不能覆盖原值；重复补录请求不产生第二条", () -> {
            long beforeCount = backfillMapper.selectCount(new QueryWrapper<>());
            Map<String, Object> body = new HashMap<>();
            body.put("taskId", taskId1);
            body.put("indicatorCode", "PH");
            body.put("valueText", "8.3");
            body.put("backfillDate", "2026-10-05");
            body.put("requestId", "bf-001");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/wq/backfills", body));
            assertSuccess(resp, "补录pH历史值");
            Long bfId = resp.path("data").path("id").asLong();

            // 原测量项值必须保持 8.1，不被补录值 8.3 覆盖
            WqTaskItem ph = findItem(itemsOf(taskId1), "PH");
            check("8.1".equals(ph.getValueText()), "补录覆盖了原采样值：" + ph.getValueText());
            List<WqBackfill> rows = backfillMapper.selectList(
                    new QueryWrapper<WqBackfill>().eq("task_item_id", ph.getId()));
            check(rows.size() == 1 && "8.3".equals(rows.get(0).getValueText()),
                    "补录应以追加行落库8.3，实际行数=" + rows.size());

            // 同一请求再补录一次：不能产生第二条结果
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/wq/backfills", body));
            assertSuccess(again, "重复补录请求");
            check(again.path("data").path("id").asLong() == bfId, "重复补录应返回首次补录记录");
            long afterCount = backfillMapper.selectCount(new QueryWrapper<>());
            check(afterCount == beforeCount + 1, "重复补录产生了第二条记录：" + beforeCount + "->" + afterCount);
        });

        checkpoint("CP10", "主管审核：驳回必须填意见，驳回后不进健康曲线", () -> {
            Map<String, Object> noReason = new HashMap<>();
            noReason.put("approved", false);
            JsonNode rejected = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/review", noReason));
            check(!rejected.path("success").asBoolean(true), "驳回无意见应被拒绝");

            Map<String, Object> reject = new HashMap<>();
            reject.put("approved", false);
            reject.put("remark", "盐度缺测需现场复核");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/review", reject));
            assertSuccess(resp, "主管驳回");
            check(WqTask.STATUS_REJECTED.equals(resp.path("data").path("status").asText()),
                    "驳回后状态应为REJECTED");
            Long curveCount = healthPointMapper.selectCount(
                    new QueryWrapper<WqHealthPoint>().eq("pond_id", pondId1));
            check(curveCount != null && curveCount == 0L, "驳回任务不应写入健康曲线，实际点数=" + curveCount);
        });

        checkpoint("CP11", "重新提交并审核通过后才进健康曲线；重复审核不重复写入", () -> {
            JsonNode resubmit = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/submit", submit(null, "submit-002")));
            assertSuccess(resubmit, "驳回后重新提交");
            check(WqTask.STATUS_SUBMITTED.equals(resubmit.path("data").path("status").asText()),
                    "重新提交后应回到SUBMITTED");

            Map<String, Object> approve = new HashMap<>();
            approve.put("approved", true);
            approve.put("remark", "复核无误");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/review", approve));
            assertSuccess(resp, "主管审核通过");
            check(WqTask.STATUS_APPROVED.equals(resp.path("data").path("status").asText()),
                    "审核后状态应为APPROVED");

            List<WqHealthPoint> points = healthPointMapper.selectList(
                    new QueryWrapper<WqHealthPoint>().eq("pond_id", pondId1).orderByAsc("indicator_code"));
            check(points.size() == 2, "已测指标PH/DO应入曲线共2点（缺测盐度不入），实际=" + points.size());
            check("DO".equals(points.get(0).getIndicatorCode()) && "PH".equals(points.get(1).getIndicatorCode()),
                    "曲线指标不符：" + points.get(0).getIndicatorCode() + "," + points.get(1).getIndicatorCode());
            check(new BigDecimal("8.1").compareTo(points.get(1).getSampleValue()) == 0,
                    "曲线必须取原采样值8.1而非补录值8.3：" + points.get(1).getSampleValue());

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/wq/tasks/" + taskId1 + "/review", approve));
            assertSuccess(again, "重复审核请求");
            Long count = healthPointMapper.selectCount(
                    new QueryWrapper<WqHealthPoint>().eq("pond_id", pondId1));
            check(count != null && count == 2L, "重复审核又写入了曲线点，实际=" + count);
        });

        checkpoint("CP12", "新规则版本V2只作用于未开始任务，已审核任务仍冻结V1", () -> {
            // 第二个在执行计划（苗种期），生成任务后全部未开始
            Map<String, Object> plan = new HashMap<>();
            plan.put("planNo", "WQ-PLAN-002");
            plan.put("planName", "下一茬苗种期计划");
            plan.put("ruleVersionId", versionV1);
            plan.put("stageCode", "NURSERY");
            plan.put("startDate", "2026-11-01");
            plan.put("endDate", "2026-11-07");
            plan.put("pondIds", list(pondId2));
            JsonNode planResp = json(call(HttpMethod.POST, "/api/aqua/wq/plans", plan));
            assertSuccess(planResp, "创建计划2");
            planId2 = planResp.path("data").path("id").asLong();
            JsonNode gen = json(call(HttpMethod.POST,
                    "/api/aqua/wq/plans/" + planId2 + "/tasks/generate",
                    mapOf("requestId", "gen-002")));
            check(gen.path("data").path("createdCount").asInt() == 3,
                    "计划2应生成3条任务（11-01/04/07）");
            Long unstartedTaskId = gen.path("data").path("created").get(0).path("id").asLong();

            // 发布V2：苗种期范围调整并新增氨氮指标
            Map<String, Object> v2 = ruleVersionBody("WQ-RULE-V2",
                    stage("NURSERY", "苗种期", 2,
                            indicator("PH", "pH", "", "7.8", "8.6"),
                            indicator("SALINITY", "盐度", "ppt", "26.0", "33.0"),
                            indicator("DO", "溶解氧", "mg/L", "5.2", "9.0"),
                            indicator("AMMONIA", "氨氮", "mg/L", "0.0", "0.2")),
                    stage("GROWOUT", "养成期", 5,
                            indicator("PH", "pH", "", "7.7", "8.7")));
            JsonNode v2Resp = json(call(HttpMethod.POST, "/api/aqua/wq/rule-versions", v2));
            assertSuccess(v2Resp, "发布规则版本V2");
            versionV2 = v2Resp.path("data").path("id").asLong();

            // V1 已停用
            check(WqRuleVersion.STATUS_SUPERSEDED.equals(versionMapper.selectById(versionV1).getStatus()),
                    "发布V2后V1应停用");

            // 未开始任务重绑V2：指标快照按V2重建（4项，pH下限7.8）
            WqTask unstarted = taskMapper.selectById(unstartedTaskId);
            check(versionV2.equals(unstarted.getRuleVersionId()),
                    "未开始任务应套用V2，实际版本id=" + unstarted.getRuleVersionId());
            List<WqTaskItem> newItems = itemsOf(unstartedTaskId);
            check(newItems.size() == 4, "未开始任务应重建为V2的4个指标，实际=" + newItems.size());
            WqTaskItem ph = findItem(newItems, "PH");
            WqTaskItem ammonia = findItem(newItems, "AMMONIA");
            check(new BigDecimal("7.8").compareTo(ph.getNormalMin()) == 0,
                    "未开始任务pH范围应更新为V2的7.8：" + ph.getNormalMin());
            check(ammonia != null, "未开始任务应出现V2新增的氨氮指标");

            // 已审核任务仍冻结V1：3个指标、pH下限7.5、曲线值不变
            WqTask approved = taskMapper.selectById(taskId1);
            check(versionV1.equals(approved.getRuleVersionId()),
                    "已审核任务必须继续冻结V1，实际版本id=" + approved.getRuleVersionId());
            List<WqTaskItem> oldItems = itemsOf(taskId1);
            check(oldItems.size() == 3, "已审核任务指标快照不应被V2改动，实际=" + oldItems.size());
            check(new BigDecimal("7.5").compareTo(findItem(oldItems, "PH").getNormalMin()) == 0,
                    "已审核任务pH范围应保持V1的7.5");
            Long curve = healthPointMapper.selectCount(
                    new QueryWrapper<WqHealthPoint>().eq("pond_id", pondId1));
            check(curve != null && curve == 2L, "V2发布不应改动既有健康曲线，实际点数=" + curve);
        });

        checkpoint("CP13", "所有修改保留证据（生成/提交/补录/驳回/通过/版本切换）", () -> {
            check(evidenceCount(WqChangeEvidence.ACTION_GENERATE, null) >= 9,
                    "任务生成证据应至少9条");
            check(evidenceCount(WqChangeEvidence.ACTION_BACKFILL, null) >= 1, "补录证据缺失");
            check(evidenceCount(WqChangeEvidence.ACTION_APPROVE, taskId1) == 1, "审核通过证据应为1条");
            check(evidenceCount(WqChangeEvidence.ACTION_REJECT, taskId1) == 1, "驳回证据应为1条");
            // 旧版本停用证据
            Long versionUpdate = evidenceMapper.selectCount(new QueryWrapper<WqChangeEvidence>()
                    .eq("entity_type", WqChangeEvidence.ENTITY_RULE_VERSION)
                    .eq("entity_id", versionV1)
                    .eq("action", WqChangeEvidence.ACTION_UPDATE));
            check(versionUpdate != null && versionUpdate == 1L, "旧版本停用证据缺失");
            // 补录证据必须保留原值 8.1 的痕迹
            List<WqChangeEvidence> bfEvidence = evidenceMapper.selectList(
                    new QueryWrapper<WqChangeEvidence>()
                            .eq("entity_type", WqChangeEvidence.ENTITY_BACKFILL)
                            .eq("request_id", "bf-001"));
            check(bfEvidence.size() == 1 && bfEvidence.get(0).getDetail().contains("8.1"),
                    "补录证据未保留原值8.1");
            // 未开始任务重绑证据
            Long rebind = evidenceMapper.selectCount(new QueryWrapper<WqChangeEvidence>()
                    .eq("entity_type", WqChangeEvidence.ENTITY_TASK)
                    .eq("action", WqChangeEvidence.ACTION_UPDATE)
                    .like("detail", "新规则版本"));
            check(rebind != null && rebind >= 1L, "未开始任务重绑证据缺失，实际=" + rebind);
        });

        report.add("验收结论：全部检查点通过——计划按池生成、重复请求不产生第二条结果、缺测必须说明原因、补录只追加、新版本只作用于未开始任务、审核后才进健康曲线、修改均留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", "东山湾海区");
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> indicator(String code, String name, String unit, String min, String max) {
        Map<String, Object> m = new HashMap<>();
        m.put("indicatorCode", code);
        m.put("indicatorName", name);
        m.put("unit", unit);
        m.put("minValue", min);
        m.put("maxValue", max);
        m.put("requiredFlag", true);
        return m;
    }

    private Map<String, Object> stage(String code, String name, int interval, Map<String, Object>... inds) {
        Map<String, Object> m = new HashMap<>();
        m.put("stageCode", code);
        m.put("stageName", name);
        m.put("sampleIntervalDays", interval);
        m.put("indicators", list(inds));
        return m;
    }

    @SafeVarargs
    private final Map<String, Object> ruleVersionBody(String versionNo, Map<String, Object>... stages) {
        Map<String, Object> m = new HashMap<>();
        m.put("versionNo", versionNo);
        m.put("remark", "验收规则版本" + versionNo);
        m.put("stages", list(stages));
        return m;
    }

    private Map<String, Object> itemInput(String code, String value, boolean missing, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("indicatorCode", code);
        m.put("valueText", value);
        m.put("missingFlag", missing);
        m.put("missingReason", reason);
        return m;
    }

    private Map<String, Object> draft(Map<String, Object> item) {
        Map<String, Object> m = new HashMap<>();
        m.put("items", list(item));
        return m;
    }

    private Map<String, Object> submit(List<Map<String, Object>> items, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("items", items);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> mapOf(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    @SafeVarargs
    private final <T> List<T> list(T... values) {
        List<T> l = new ArrayList<>();
        for (T v : values) {
            l.add(v);
        }
        return l;
    }

    private List<WqTaskItem> itemsOf(Long taskId) {
        return itemMapper.selectList(
                new QueryWrapper<WqTaskItem>().eq("task_id", taskId).orderByAsc("id"));
    }

    private WqTaskItem findItem(List<WqTaskItem> items, String code) {
        for (WqTaskItem item : items) {
            if (code.equals(item.getIndicatorCode())) {
                return item;
            }
        }
        return null;
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<WqChangeEvidence> wrapper = new QueryWrapper<WqChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId)
                    .eq("entity_type", WqChangeEvidence.ENTITY_TASK);
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
        sb.append("\n========== 水质采样计划与缺测补录验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=====================================================\n");
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
