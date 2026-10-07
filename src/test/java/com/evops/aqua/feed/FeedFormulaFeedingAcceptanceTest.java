package com.evops.aqua.feed;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.feed.entity.FeedBatch;
import com.evops.aqua.feed.entity.FeedBatchLock;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.entity.FeedOrderItem;
import com.evops.aqua.feed.mapper.FeedBatchLockMapper;
import com.evops.aqua.feed.mapper.FeedBatchMapper;
import com.evops.aqua.feed.mapper.FeedChangeEvidenceMapper;
import com.evops.aqua.feed.mapper.FeedFormulaVersionMapper;
import com.evops.aqua.feed.mapper.FeedOrderItemMapper;
import com.evops.aqua.feed.mapper.FeedOrderMapper;
import com.evops.aqua.mapper.PondMapper;
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
 * 饲料批号管理与配方投喂验收（aqua-3）。
 * 主线：批号入库（三量账）→ 配方按苗种/日龄/水温版本化 → 投喂单准备即冻结版本快照
 * → 未锁定不得执行 → 过期批号/库存不足拦截 → 锁定占用批号 → 临时减量登记原因审批人
 * → 执行转消耗 → 日汇总试喂/正式分列 → 配方新版本不动历史快照
 * → 权限收紧批号停止推进而事实保留 → 放开恢复 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeedFormulaFeedingAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private FeedBatchMapper batchMapper;
    @Autowired
    private FeedBatchLockMapper lockMapper;
    @Autowired
    private FeedOrderMapper orderMapper;
    @Autowired
    private FeedOrderItemMapper itemMapper;
    @Autowired
    private FeedFormulaVersionMapper versionMapper;
    @Autowired
    private FeedChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId1;
    private Long pondId2;
    private Long batchCroaker;   // 大黄鱼批次
    private Long batchGrouper;   // 石斑鱼批次
    private Long expiredBatchId;
    private Long freshBatchId;
    private Long formulaV1;
    private Long formulaV2;
    private Long formalOrderId;
    private Long formalItemId;
    private Long trialOrderId;
    private Long v2OrderId;

    @Test
    void acceptanceFeedBatchAndFormulaFeeding() {
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
            pondId1 = createPond("FD-P01", "饲料1号池");
            pondId2 = createPond("FD-P02", "饲料2号池");
            Pond pond = pondMapper.selectById(pondId1);
            check(pond != null && "饲料1号池".equals(pond.getPondName()), "养殖池落库异常");
        });

        checkpoint("CP2", "投苗：大黄鱼、石斑鱼各一批（投苗日2026-10-01，作为日龄基准）", () -> {
            batchCroaker = stockBatch("FD-C01", pondId1, "LARGE_YELLOW_CROAKER", "大黄鱼苗");
            batchGrouper = stockBatch("FD-G01", pondId2, "GROUPER", "石斑鱼苗");
            check(batchCroaker != null && batchGrouper != null, "两个投苗批次应落库");
        });

        checkpoint("CP3", "登记饲料批号：先只登记一个已过期批号（到期10-04，投喂日10-05已过期）", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feed/batches",
                    feedBatchBody("FB-EXPIRED", "PELLET_A", "100", "2026-10-04")));
            assertSuccess(resp, "登记过期批号");
            expiredBatchId = resp.path("data").path("id").asLong();
            FeedBatch b = batchMapper.selectById(expiredBatchId);
            check(b.getQuantityLocked().signum() == 0 && b.getQuantityConsumed().signum() == 0,
                    "新批号锁定量与消耗量初始应为0");
        });

        checkpoint("CP4", "发布大黄鱼配方V1：日龄0-30、水温20-28喂PELLET_A计划10kg", () -> {
            Map<String, Object> body = formulaBody("FF-CROAKER-V1", "LARGE_YELLOW_CROAKER",
                    rule(0, 30, "20.00", "28.00", "PELLET_A", "10"));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feed/formulas", body));
            assertSuccess(resp, "发布配方V1");
            formulaV1 = resp.path("data").path("id").asLong();
            check(FeedFormulaVersion.STATUS_ACTIVE.equals(
                    versionMapper.selectById(formulaV1).getStatus()), "V1应为生效版本");
        });

        checkpoint("CP5", "准备正式投喂单：10-05日龄4、水温25命中V1，建立即冻结版本与计划量快照", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feed/orders/prepare",
                    orderBody(pondId1, batchCroaker, "2026-10-05", "25.00", "FORMAL", "prep-1")));
            assertSuccess(resp, "准备正式投喂单");
            formalOrderId = resp.path("data").path("id").asLong();
            check(resp.path("data").path("formulaVersionId").asLong() == formulaV1, "投喂单应冻结配方V1");
            check(resp.path("data").path("dayAge").asInt() == 4, "日龄应由投苗日推算为4");
            check(FeedOrder.STATUS_DRAFT.equals(resp.path("data").path("status").asText()),
                    "新投喂单应为草稿DRAFT");
            FeedOrderItem item = onlyItem(formalOrderId);
            formalItemId = item.getId();
            check("PELLET_A".equals(item.getFeedName())
                    && new BigDecimal("10").compareTo(item.getPlannedQtyKg()) == 0,
                    "明细应冻结饲料PELLET_A与计划量10kg");
        });

        checkpoint("CP6", "投喂单未锁定直接执行必须被拦截", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/execute", action("exec-1")));
            check(!resp.path("success").asBoolean(true), "草稿态投喂单直接执行应被拒绝");
            check(resp.path("message").asText().contains("锁定"), "提示应要求先锁定：" + resp.path("message").asText());
            check(FeedOrder.STATUS_DRAFT.equals(orderMapper.selectById(formalOrderId).getStatus()),
                    "拒绝执行后投喂单应仍是草稿");
        });

        checkpoint("CP7", "唯一批号已过期：锁定投喂单必须被拦截（过期批号禁止占用）", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/lock", action("lock-1")));
            check(!resp.path("success").asBoolean(true), "存在过期批号时锁定应被拦截");
            check(resp.path("message").asText().contains("未过期"),
                    "应提示无未过期批号：" + resp.path("message").asText());
            check(batchMapper.selectById(expiredBatchId).getQuantityLocked().signum() == 0,
                    "拦截后过期批号不得产生任何占用");
            check(lockMapper.selectCount(new QueryWrapper<>()) == 0L, "拦截后不得残留批号占用行");
        });

        checkpoint("CP8", "补登未过期批号30kg后锁定成功：按先到期先用占用新批号而非过期批号", () -> {
            JsonNode in = json(call(HttpMethod.POST, "/api/aqua/feed/batches",
                    feedBatchBody("FB-FRESH", "PELLET_A", "30", "2026-10-31")));
            assertSuccess(in, "登记新鲜批号");
            freshBatchId = in.path("data").path("id").asLong();

            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/lock", action("lock-2")));
            assertSuccess(resp, "锁定投喂单");
            check(FeedOrder.STATUS_LOCKED.equals(resp.path("data").path("status").asText()),
                    "锁定后状态应为LOCKED");
            FeedBatch fresh = batchMapper.selectById(freshBatchId);
            check(new BigDecimal("10").compareTo(fresh.getQuantityLocked()) == 0,
                    "新鲜批号应被占用10kg，实际=" + fresh.getQuantityLocked());
            check(batchMapper.selectById(expiredBatchId).getQuantityLocked().signum() == 0,
                    "过期批号不得被占用");
            List<FeedBatchLock> locks = lockMapper.selectList(new QueryWrapper<FeedBatchLock>()
                    .eq("order_item_id", formalItemId));
            check(locks.size() == 1 && locks.get(0).getFeedBatchId().equals(freshBatchId),
                    "占用明细必须指向未过期的新鲜批号");
        });

        checkpoint("CP9", "库存不足拦截：石斑鱼配方需PELLET_B 10kg，仅入库3kg，锁定应失败且不留半占用", () -> {
            JsonNode fv = json(call(HttpMethod.POST, "/api/aqua/feed/formulas",
                    formulaBody("FF-GROUPER-V1", "GROUPER",
                            rule(0, 30, "20.00", "28.00", "PELLET_B", "10"))));
            assertSuccess(fv, "发布石斑鱼配方V1");
            JsonNode fb = json(call(HttpMethod.POST, "/api/aqua/feed/batches",
                    feedBatchBody("FB-B-SMALL", "PELLET_B", "3", "2026-10-31")));
            assertSuccess(fb, "登记少量PELLET_B批号");
            Long smallBatchId = fb.path("data").path("id").asLong();

            JsonNode prep = json(call(HttpMethod.POST, "/api/aqua/feed/orders/prepare",
                    orderBody(pondId2, batchGrouper, "2026-10-05", "25.00", "FORMAL", "prep-g")));
            assertSuccess(prep, "准备石斑鱼投喂单");
            Long grouperOrderId = prep.path("data").path("id").asLong();

            JsonNode locked = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + grouperOrderId + "/lock", action("lock-g")));
            check(!locked.path("success").asBoolean(true), "可用库存不足应拦截锁定");
            check(locked.path("message").asText().contains("库存不足"),
                    "应提示库存不足：" + locked.path("message").asText());
            check(FeedOrder.STATUS_DRAFT.equals(orderMapper.selectById(grouperOrderId).getStatus()),
                    "库存不足拦截后投喂单应仍是草稿");
            check(batchMapper.selectById(smallBatchId).getQuantityLocked().signum() == 0,
                    "库存不足不得产生部分占用（事务回滚）");
        });

        checkpoint("CP10", "临时减量：缺原因或缺审批人被拒；合法减量10→6登记原因审批人并释放4kg占用", () -> {
            Map<String, Object> noApprover = reduction("6", "天气突变减量", null);
            JsonNode r1 = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/items/" + formalItemId + "/reduction",
                    noApprover));
            check(!r1.path("success").asBoolean(true), "缺审批人的减量应被参数校验拒绝");

            Map<String, Object> tooBig = reduction("10", "天气突变减量", "主管王海");
            JsonNode r2 = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/items/" + formalItemId + "/reduction",
                    tooBig));
            check(!r2.path("success").asBoolean(true), "减量后量未小于锁定量应被拒绝");

            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/items/" + formalItemId + "/reduction",
                    reduction("6", "寒潮预警摄食下降，临时减量", "主管王海")));
            assertSuccess(ok, "合法临时减量");
            FeedOrderItem item = itemMapper.selectById(formalItemId);
            check(Boolean.TRUE.equals(item.getReductionFlag()), "减量标记应为true");
            check(new BigDecimal("6").compareTo(item.getActualQtyKg()) == 0, "实际量应为6kg");
            check("主管王海".equals(item.getReductionApprovedBy())
                    && item.getReductionReason().contains("寒潮"), "减量原因与审批人必须落库");
            check(new BigDecimal("6").compareTo(batchMapper.selectById(freshBatchId).getQuantityLocked()) == 0,
                    "减量应释放4kg占用，剩余锁定6kg，实际="
                            + batchMapper.selectById(freshBatchId).getQuantityLocked());
        });

        checkpoint("CP11", "执行投喂：锁定占用转实际消耗6kg；重复执行幂等不重复消耗", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/execute", action("exec-formal")));
            assertSuccess(resp, "执行正式投喂");
            check(FeedOrder.STATUS_EXECUTED.equals(resp.path("data").path("status").asText()),
                    "执行后状态应为EXECUTED");
            FeedBatch fresh = batchMapper.selectById(freshBatchId);
            check(new BigDecimal("6").compareTo(fresh.getQuantityConsumed()) == 0,
                    "应消耗6kg，实际=" + fresh.getQuantityConsumed());
            check(fresh.getQuantityLocked().signum() == 0, "执行后锁定占用应清零");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + formalOrderId + "/execute", action("exec-formal")));
            assertSuccess(again, "重复执行请求");
            check(new BigDecimal("6").compareTo(
                    batchMapper.selectById(freshBatchId).getQuantityConsumed()) == 0,
                    "重复执行不得重复消耗");
        });

        checkpoint("CP12", "日汇总试喂与正式投喂严格分列，且只统计已执行投喂", () -> {
            // 同日再建一张试喂单并执行（计划10kg，不减量）
            JsonNode prep = json(call(HttpMethod.POST, "/api/aqua/feed/orders/prepare",
                    orderBody(pondId1, batchCroaker, "2026-10-05", "25.00", "TRIAL", "prep-trial")));
            assertSuccess(prep, "准备试喂单");
            trialOrderId = prep.path("data").path("id").asLong();
            check(FeedOrder.TYPE_TRIAL.equals(prep.path("data").path("feedType").asText()),
                    "投喂单类型应为TRIAL");
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + trialOrderId + "/lock", action("lock-trial"))), "锁定试喂单");
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + trialOrderId + "/execute", action("exec-trial"))), "执行试喂单");
            check(new BigDecimal("16").compareTo(batchMapper.selectById(freshBatchId).getQuantityConsumed()) == 0,
                    "累计消耗应为正式6+试喂10=16kg");

            JsonNode sum = json(call(HttpMethod.GET,
                    "/api/aqua/feed/orders/daily-summary?pondId=" + pondId1 + "&feedDate=2026-10-05", null));
            assertSuccess(sum, "查询日汇总");
            JsonNode formal = sum.path("data").path("formal");
            JsonNode trial = sum.path("data").path("trial");
            check(formal.path("orderCount").asInt() == 1
                    && new BigDecimal("6").compareTo(formal.path("totalQuantityKg").decimalValue()) == 0,
                    "正式投喂应为1单合计6kg，实际=" + formal.path("totalQuantityKg").asText());
            check(trial.path("orderCount").asInt() == 1
                    && new BigDecimal("10").compareTo(trial.path("totalQuantityKg").decimalValue()) == 0,
                    "试喂应为1单合计10kg，实际=" + trial.path("totalQuantityKg").asText());
        });

        checkpoint("CP13", "配方调整发布V2（计划改12kg）：V1停用，历史投喂仍引用V1旧快照，新单冻结V2", () -> {
            JsonNode v2 = json(call(HttpMethod.POST, "/api/aqua/feed/formulas",
                    formulaBody("FF-CROAKER-V2", "LARGE_YELLOW_CROAKER",
                            rule(0, 30, "20.00", "28.00", "PELLET_A", "12"))));
            assertSuccess(v2, "发布配方V2");
            formulaV2 = v2.path("data").path("id").asLong();
            check(FeedFormulaVersion.STATUS_SUPERSEDED.equals(
                    versionMapper.selectById(formulaV1).getStatus()), "发布V2后V1应停用");
            check(FeedFormulaVersion.STATUS_ACTIVE.equals(
                    versionMapper.selectById(formulaV2).getStatus()), "V2应为生效版本");

            // 历史已执行投喂单继续引用V1，计划量快照保持10kg
            JsonNode old = json(call(HttpMethod.GET,
                    "/api/aqua/feed/orders/" + formalOrderId, null));
            check(old.path("data").path("order").path("formulaVersionId").asLong() == formulaV1,
                    "历史投喂单必须继续引用配方V1");
            check(new BigDecimal("10").compareTo(
                    old.path("data").path("items").get(0).path("item").path("plannedQtyKg").decimalValue()) == 0,
                    "历史投喂单计划量快照必须保持V1的10kg");

            // 新投喂单按V2命中12kg并冻结V2
            JsonNode prep = json(call(HttpMethod.POST, "/api/aqua/feed/orders/prepare",
                    orderBody(pondId1, batchCroaker, "2026-10-07", "25.00", "FORMAL", "prep-v2")));
            assertSuccess(prep, "按V2准备新投喂单");
            v2OrderId = prep.path("data").path("id").asLong();
            check(prep.path("data").path("formulaVersionId").asLong() == formulaV2, "新单应冻结V2");
            check(new BigDecimal("12").compareTo(onlyItem(v2OrderId).getPlannedQtyKg()) == 0,
                    "新单计划量应为V2的12kg");
        });

        checkpoint("CP14", "收紧前锁定V2新单（占用12kg），为收紧期拦截消耗做准备", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + v2OrderId + "/lock", action("lock-v2")));
            assertSuccess(resp, "锁定V2投喂单");
            check(new BigDecimal("12").compareTo(batchMapper.selectById(freshBatchId).getQuantityLocked()) == 0,
                    "V2单应占用12kg，累计消耗仍16kg");
        });

        checkpoint("CP15", "权限收紧：饲料批号停止推进——新批号入库、已锁定单执行消耗均被拦截，已确认事实不变", () -> {
            Map<String, Object> reason = new HashMap<>();
            reason.put("reason", "海区管控升级，暂停饲料投入");
            reason.put("requestId", "tighten-1");
            JsonNode t = json(call(HttpMethod.POST, "/api/aqua/feed/access/tighten", reason));
            assertSuccess(t, "收紧权限");
            check(t.path("data").path("feedBatchAdvanceEnabled").asBoolean(true) == false,
                    "开关应为false");

            // 新批号入库被拦截
            JsonNode newBatch = json(call(HttpMethod.POST, "/api/aqua/feed/batches",
                    feedBatchBody("FB-BLOCKED", "PELLET_A", "50", "2026-11-30")));
            check(!newBatch.path("success").asBoolean(true), "收紧期登记新批号应被拒绝");
            check(newBatch.path("message").asText().contains("停止推进"),
                    "应提示批号停止推进：" + newBatch.path("message").asText());

            // 已锁定单执行消耗被拦截
            JsonNode exec = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + v2OrderId + "/execute", action("exec-v2-blocked")));
            check(!exec.path("success").asBoolean(true), "收紧期执行投喂消耗应被拒绝");
            check(FeedOrder.STATUS_LOCKED.equals(orderMapper.selectById(v2OrderId).getStatus()),
                    "被拦截的投喂单应仍为LOCKED");

            // 已确认事实保留：累计消耗仍16、V2单仍锁12、配方版本状态不变、历史单仍EXECUTED
            FeedBatch fresh = batchMapper.selectById(freshBatchId);
            check(new BigDecimal("16").compareTo(fresh.getQuantityConsumed()) == 0
                            && new BigDecimal("12").compareTo(fresh.getQuantityLocked()) == 0,
                    "收紧期批号台账必须保持：消耗16、锁定12");
            check(FeedFormulaVersion.STATUS_SUPERSEDED.equals(versionMapper.selectById(formulaV1).getStatus())
                            && FeedFormulaVersion.STATUS_ACTIVE.equals(versionMapper.selectById(formulaV2).getStatus()),
                    "收紧不得改动已确认的配方版本");
            check(FeedOrder.STATUS_EXECUTED.equals(orderMapper.selectById(formalOrderId).getStatus())
                            && FeedOrder.STATUS_EXECUTED.equals(orderMapper.selectById(trialOrderId).getStatus()),
                    "收紧不得改动历史投喂事实");
        });

        checkpoint("CP16", "放开权限后批号恢复推进，V2单得以执行；全程留证齐全", () -> {
            Map<String, Object> reason = new HashMap<>();
            reason.put("reason", "管控解除，恢复投喂");
            reason.put("requestId", "relax-1");
            JsonNode r = json(call(HttpMethod.POST, "/api/aqua/feed/access/relax", reason));
            assertSuccess(r, "放开权限");
            check(r.path("data").path("feedBatchAdvanceEnabled").asBoolean(false), "开关应恢复true");

            JsonNode exec = json(call(HttpMethod.POST,
                    "/api/aqua/feed/orders/" + v2OrderId + "/execute", action("exec-v2-ok")));
            assertSuccess(exec, "放开后执行V2投喂单");
            check(new BigDecimal("28").compareTo(batchMapper.selectById(freshBatchId).getQuantityConsumed()) == 0,
                    "累计消耗应为16+12=28kg");

            check(evidenceCount(FeedChangeEvidence.ACTION_LOCK, null) >= 4, "应保留锁定证据");
            check(evidenceCount(FeedChangeEvidence.ACTION_EXECUTE, null) >= 3, "应保留执行证据");
            check(evidenceCount(FeedChangeEvidence.ACTION_REDUCE, formalItemId) == 1, "减量证据应为1条");
            check(evidenceCount(FeedChangeEvidence.ACTION_TIGHTEN, FeedAccessPolicyId()) == 1, "收紧证据缺失");
            check(evidenceCount(FeedChangeEvidence.ACTION_RELAX, FeedAccessPolicyId()) == 1, "放开证据缺失");
            List<FeedChangeEvidence> reduceEv = evidenceMapper.selectList(new QueryWrapper<FeedChangeEvidence>()
                    .eq("entity_type", FeedChangeEvidence.ENTITY_ORDER_ITEM)
                    .eq("action", FeedChangeEvidence.ACTION_REDUCE));
            check(reduceEv.size() == 1 && reduceEv.get(0).getDetail().contains("主管王海")
                            && reduceEv.get(0).getDetail().contains("寒潮"),
                    "减量证据必须保留原因与审批人");
        });

        report.add("验收结论：全部检查点通过——批号三量账、过期/库存不足拦截、未锁定不得执行、配方按苗种日龄水温版本化且历史冻结旧快照、临时减量登记原因审批人、日汇总试喂正式分列、权限收紧批号停止推进而事实保留、全程留证");
        printReport();
    }

    private Long FeedAccessPolicyId() {
        return 1L;
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

    private Long stockBatch(String batchNo, Long pondId, String species, String source) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", species);
        body.put("seedSource", source);
        body.put("quantity", 50000);
        body.put("stockDate", "2026-10-01");
        body.put("plannedHarvestDate", "2027-03-01");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> feedBatchBody(String no, String feed, String qty, String expiry) {
        Map<String, Object> m = new HashMap<>();
        m.put("batchNo", no);
        m.put("feedName", feed);
        m.put("supplier", "海西饲料厂");
        m.put("quantityIn", qty);
        m.put("productionDate", "2026-08-01");
        m.put("expiryDate", expiry);
        return m;
    }

    private Map<String, Object> rule(int ageFrom, int ageTo, String tempFrom, String tempTo,
                                     String feed, String qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("dayAgeFrom", ageFrom);
        m.put("dayAgeTo", ageTo);
        m.put("waterTempFrom", tempFrom);
        m.put("waterTempTo", tempTo);
        m.put("feedName", feed);
        m.put("plannedQtyKg", qty);
        return m;
    }

    private Map<String, Object> formulaBody(String versionNo, String species, Map<String, Object> rule) {
        Map<String, Object> m = new HashMap<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(rule);
        m.put("versionNo", versionNo);
        m.put("species", species);
        m.put("remark", "验收配方" + versionNo);
        m.put("rules", rules);
        return m;
    }

    private Map<String, Object> orderBody(Long pondId, Long batchId, String date, String temp,
                                          String type, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("pondId", pondId);
        m.put("batchId", batchId);
        m.put("feedDate", date);
        m.put("waterTempC", temp);
        m.put("feedType", type);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> reduction(String actual, String reason, String approver) {
        Map<String, Object> m = new HashMap<>();
        m.put("actualQtyKg", actual);
        m.put("reductionReason", reason);
        m.put("reductionApprovedBy", approver);
        return m;
    }

    private Map<String, Object> action(String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("requestId", requestId);
        return m;
    }

    private FeedOrderItem onlyItem(Long orderId) {
        List<FeedOrderItem> items = itemMapper.selectList(
                new QueryWrapper<FeedOrderItem>().eq("order_id", orderId));
        check(items.size() == 1, "投喂单应恰有1条明细，实际=" + items.size());
        return items.get(0);
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<FeedChangeEvidence> wrapper = new QueryWrapper<FeedChangeEvidence>()
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
        sb.append("\n========== 饲料批号与配方投喂验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("===============================================\n");
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
