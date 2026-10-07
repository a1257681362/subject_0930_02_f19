package com.evops.aqua.noculture;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.noculture.entity.NoCultureChangeEvidence;
import com.evops.aqua.noculture.entity.NoCultureCompensation;
import com.evops.aqua.noculture.entity.NoCultureFreeze;
import com.evops.aqua.noculture.entity.NoCultureMatch;
import com.evops.aqua.noculture.entity.NoCultureRule;
import com.evops.aqua.noculture.mapper.NoCultureChangeEvidenceMapper;
import com.evops.aqua.noculture.mapper.NoCultureCompensationMapper;
import com.evops.aqua.noculture.mapper.NoCultureFreezeMapper;
import com.evops.aqua.noculture.mapper.NoCultureMatchMapper;
import com.evops.aqua.noculture.mapper.NoCultureRuleMapper;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 海区禁养规则验收（aqua-11）。
 * 主线：维护校验 → 登记多边形规则冻结在途、已完成只提示 → 投苗/转池/捕捞空间时间拦截
 * → 半开时间边界 → 空间重叠按更严格规则 → 撤销部分解冻（仍被他规冻结的补偿挂起）
 * → 补偿与空间匹配账账对应（勾稽平衡）→ 全部解冻 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NoCultureAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private NoCultureRuleMapper ruleMapper;
    @Autowired
    private NoCultureMatchMapper matchMapper;
    @Autowired
    private NoCultureFreezeMapper freezeMapper;
    @Autowired
    private NoCultureCompensationMapper compensationMapper;
    @Autowired
    private NoCultureChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondIn;      // 东山湾，坐标在多边形内
    private Long pondEdge;    // 东山湾，坐标在多边形外
    private Long pondOther;   // 西沙湾
    private Long pondTime;    // 时间湾（仅用于时间边界）
    private Long batchIn;
    private Long batchEdge;
    private Long batchOther;
    private Long batchDone;
    private Long r1;          // 多边形规则，严格级1
    private Long r2;          // 时间湾整海区规则（有限期）
    private Long r3;          // 东山湾整海区规则，严格级5（更严）

    // 矩形多边形，包围 (120.10,27.10)，不包围 (121.90,28.90)
    private final List<List<Double>> polygon = Arrays.asList(
            Arrays.asList(120.0, 27.0),
            Arrays.asList(120.3, 27.0),
            Arrays.asList(120.3, 27.3),
            Arrays.asList(120.0, 27.3));

    @Test
    void acceptanceNoCultureRule() {
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

        checkpoint("CP1", "建池：多边形内/外两口东山湾池、一口西沙湾池、一口时间湾池（带经纬度）", () -> {
            pondIn = createPond("NC-IN", "多边形内池", "禁养A海区", "120.10", "27.10");
            pondEdge = createPond("NC-EDGE", "多边形外同海区池", "禁养A海区", "121.90", "28.90");
            pondOther = createPond("NC-OTHER", "西沙湾池", "禁养B海区", "118.10", "26.10");
            pondTime = createPond("NC-TIME", "时间湾池", "禁养T海区", "122.10", "25.10");
        });

        checkpoint("CP2", "维护校验：缺依据文件/操作人/requestId、多边形不足3点、结束日不晚于开始日、整海区缺海区一律拒绝", () -> {
            Map<String, Object> noBasis = ruleBody("NC-BAD1", "禁养A海区", "AREA", null,
                    "2000-01-01", null, 1, "监管钱海", "nc-bad-1");
            noBasis.remove("basisDocNo");
            check(!json(call(HttpMethod.POST, "/api/aqua/noculture/rules", noBasis))
                    .path("success").asBoolean(true), "缺依据文件文号应被拒绝");

            Map<String, Object> noOperator = ruleBody("NC-BAD2", "禁养A海区", "AREA", null,
                    "2000-01-01", null, 1, "监管钱海", "nc-bad-2");
            noOperator.remove("operator");
            check(!json(call(HttpMethod.POST, "/api/aqua/noculture/rules", noOperator))
                    .path("success").asBoolean(true), "缺操作人应被拒绝");

            List<List<Double>> twoPoints = Arrays.asList(
                    Arrays.asList(120.0, 27.0), Arrays.asList(120.3, 27.3));
            Map<String, Object> badPolygon = ruleBody("NC-BAD3", "禁养A海区", "POLYGON", twoPoints,
                    "2000-01-01", null, 1, "监管钱海", "nc-bad-3");
            JsonNode pr = json(call(HttpMethod.POST, "/api/aqua/noculture/rules", badPolygon));
            check(!pr.path("success").asBoolean(true) && pr.path("message").asText().contains("3个顶点"),
                    "多边形不足3个顶点应被拒绝：" + pr.path("message").asText());

            Map<String, Object> badRange = ruleBody("NC-BAD4", "禁养A海区", "AREA", null,
                    "2026-12-31", "2026-01-01", 1, "监管钱海", "nc-bad-4");
            JsonNode rr = json(call(HttpMethod.POST, "/api/aqua/noculture/rules", badRange));
            check(!rr.path("success").asBoolean(true) && rr.path("message").asText().contains("半开区间"),
                    "结束日不晚于开始日应被拒绝：" + rr.path("message").asText());

            Map<String, Object> noArea = ruleBody("NC-BAD5", null, "AREA", null,
                    "2000-01-01", null, 1, "监管钱海", "nc-bad-5");
            check(!json(call(HttpMethod.POST, "/api/aqua/noculture/rules", noArea))
                    .path("success").asBoolean(true), "整海区规则缺海区应被拒绝");
            check(ruleMapper.selectCount(new QueryWrapper<NoCultureRule>()) == 0L,
                    "全部非法请求都不得落规则");
        });

        checkpoint("CP3", "规则登记前先布局批次：多边形内/外、西沙湾各一在途批；多边形内再放一已完成批", () -> {
            batchIn = stockBatch("NC-C-IN", pondIn, "2026-09-01");
            batchEdge = stockBatch("NC-C-EDGE", pondEdge, "2026-09-01");
            batchOther = stockBatch("NC-C-OTHER", pondOther, "2026-09-01");
            Long preDone = stockBatch("NC-C-DONE", pondIn, "2026-08-01");
            JsonNode harvested = json(call(HttpMethod.POST, "/api/aqua/batches/" + preDone + "/harvest",
                    harvestBody("2026-09-20", 1000)));
            assertSuccess(harvested, "规则前完成一批出池");
            batchDone = preDone;
            check(SeedlingBatch.STATUS_HARVESTED.equals(batchMapper.selectById(batchDone).getStatus()),
                    "已完成批批次状态应为HARVESTED");
        });

        checkpoint("CP4", "登记多边形规则R1（长期、严格级1）：冻结多边形内在途批；区外/异海区不冻；已完成只提示；重复登记幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC-R1", "禁养A海区", "POLYGON", polygon,
                            "2000-01-01", null, 1, "监管钱海", "nc-r1")));
            assertSuccess(resp, "登记多边形禁养规则R1");
            r1 = resp.path("data").path("rule").path("id").asLong();
            check(resp.path("data").path("frozenCount").asInt() == 1,
                    "R1应冻结1个在途批（多边形内），实际=" + resp.path("data").path("frozenCount"));
            check(resp.path("data").path("completedNoticeCount").asInt() == 1,
                    "R1应对1个已完成批只提示，实际=" + resp.path("data").path("completedNoticeCount"));

            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r1).eq("batch_id", batchIn)
                    .eq("status", NoCultureFreeze.STATUS_FROZEN)) == 1L, "多边形内在途批应被R1冻结");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r1).eq("batch_id", batchEdge)) == 0L, "多边形外同海区批不应被多边形规则冻结");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r1).eq("batch_id", batchOther)) == 0L, "异海区批不应被冻结");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", batchDone)) == 0L, "已完成批不得被冻结（只提示不回写）");
            NoCultureMatch doneMatch = matchMapper.selectOne(new QueryWrapper<NoCultureMatch>()
                    .eq("rule_id", r1).eq("batch_id", batchDone));
            check(doneMatch != null && NoCultureMatch.RESULT_COMPLETED_NOTICE.equals(doneMatch.getResult())
                            && doneMatch.getFreezeId() == null,
                    "已完成批应只留COMPLETED_NOTICE匹配且无冻结账");
            check(SeedlingBatch.STATUS_HARVESTED.equals(batchMapper.selectById(batchDone).getStatus()),
                    "已完成批台账状态必须保持HARVESTED，绝不回写");

            // 匹配记录与冻结账双向外键对应
            NoCultureMatch inMatch = matchMapper.selectOne(new QueryWrapper<NoCultureMatch>()
                    .eq("rule_id", r1).eq("batch_id", batchIn));
            check(inMatch != null && inMatch.getFreezeId() != null, "FROZEN匹配必须带冻结账id");
            NoCultureFreeze inFreeze = freezeMapper.selectById(inMatch.getFreezeId());
            check(inFreeze != null && inFreeze.getMatchId().equals(inMatch.getId()),
                    "冻结账必须回指匹配记录，双向对应");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC-R1", "禁养A海区", "POLYGON", polygon,
                            "2000-01-01", null, 1, "监管钱海", "nc-r1")));
            assertSuccess(again, "重复requestId登记R1");
            check(again.path("data").path("idempotent").asBoolean()
                            && again.path("data").path("rule").path("id").asLong() == r1,
                    "重复登记应幂等返回同一规则");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>().eq("rule_id", r1)) == 1L,
                    "重复登记不得新增冻结账");
        });

        checkpoint("CP5", "三入口拦截：被冻批捕捞/转池拒绝；向多边形内池投苗拒绝；向多边形外同海区池投苗放行", () -> {
            JsonNode harvest = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchIn + "/harvest",
                    harvestBody("2026-10-06", 1000)));
            check(!harvest.path("success").asBoolean(true) && harvest.path("message").asText().contains("禁养"),
                    "被冻批捕捞应被拒绝：" + harvest.path("message").asText());

            Map<String, Object> transfer = new HashMap<>();
            transfer.put("toPondId", pondOther);
            transfer.put("transferDate", "2026-10-06");
            transfer.put("reason", "禁养期尝试转池");
            JsonNode tr = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchIn + "/transfer", transfer));
            check(!tr.path("success").asBoolean(true) && tr.path("message").asText().contains("禁养"),
                    "被冻批转池应被拒绝：" + tr.path("message").asText());

            JsonNode stockInside = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC-C-NEW-IN", pondIn, "2026-10-06")));
            check(!stockInside.path("success").asBoolean(true)
                            && stockInside.path("message").asText().contains("禁养范围"),
                    "向多边形内在效禁养区投苗应被空间+时间拦截：" + stockInside.path("message").asText());

            JsonNode stockEdge = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC-C-NEW-EDGE", pondEdge, "2026-10-06")));
            assertSuccess(stockEdge, "向多边形外同海区池投苗应放行（空间不命中）");
        });

        checkpoint("CP6", "时间半开区间[from,to)：生效前与端点日放行，期内拒绝（用时间湾整海区规则R2）", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC-R2", "禁养T海区", "AREA", null,
                            "2026-01-01", "2026-12-31", 1, "监管孙海", "nc-r2")));
            assertSuccess(resp, "登记时间湾有限期规则R2");
            r2 = resp.path("data").path("rule").path("id").asLong();

            JsonNode before = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC-C-T1", pondTime, "2025-12-31")));
            assertSuccess(before, "生效日前一天投苗应放行");
            JsonNode edgeDay = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC-C-T2", pondTime, "2026-12-31")));
            assertSuccess(edgeDay, "结束日当天（半开端点to）投苗应放行");
            JsonNode inside = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC-C-T3", pondTime, "2026-06-01")));
            check(!inside.path("success").asBoolean(true)
                            && inside.path("message").asText().contains("2026-06-01"),
                    "生效区间内投苗应被拦截并指明日期：" + inside.path("message").asText());
        });

        checkpoint("CP7", "空间重叠按更严格规则：登记覆盖全东山湾的R3（严格级5），同点命中R1/R3时按R3裁决，边界外批也被冻结", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC-R3", "禁养A海区", "AREA", null,
                            "2000-01-01", null, 5, "监管李海", "nc-r3")));
            assertSuccess(resp, "登记东山湾整海区严格规则R3");
            r3 = resp.path("data").path("rule").path("id").asLong();
            check(resp.path("data").path("frozenCount").asInt() == 3,
                    "R3应冻结3个东山湾在途批（多边形内、多边形外原批、CP5新放的多边形外批），实际="
                            + resp.path("data").path("frozenCount"));
            check(resp.path("data").path("completedNoticeCount").asInt() == 1,
                    "R3应对已完成批再只提示一次，实际=" + resp.path("data").path("completedNoticeCount"));

            // batchIn 同时被 R1(1级) 与 R3(5级) 冻结；batchEdge 仅被 R3 冻结
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", batchIn).eq("status", NoCultureFreeze.STATUS_FROZEN)) == 2L,
                    "重叠区在途批应被两条规则同时冻结");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r3).eq("batch_id", batchEdge)
                    .eq("status", NoCultureFreeze.STATUS_FROZEN)) == 1L,
                    "多边形外批应被整海区更严规则R3冻结");

            JsonNode stockOverlap = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC-C-OVL", pondIn, "2026-10-06")));
            check(!stockOverlap.path("success").asBoolean(true)
                            && stockOverlap.path("message").asText().contains("NC-R3")
                            && stockOverlap.path("message").asText().contains("更严格"),
                    "重叠命中时应按更严格规则R3裁决：" + stockOverlap.path("message").asText());

            JsonNode edgeHarvest = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchEdge + "/harvest",
                    harvestBody("2026-10-06", 1000)));
            check(!edgeHarvest.path("success").asBoolean(true) && edgeHarvest.path("message").asText().contains("禁养"),
                    "仅被R3冻结的边界外批捕捞应被拒绝");
        });

        checkpoint("CP8", "撤销R3部分解冻：多边形外批补偿RELEASED放行，多边形内批仍被R1冻结补偿SKIPPED_STILL_BOUND；账账对应、重复撤销幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules/" + r3 + "/revoke",
                    revokeBody("监管李海", "文件到期撤销R3", "nc-r3-rev")));
            assertSuccess(resp, "撤销R3");
            check(resp.path("data").path("releasedCount").asInt() == 2,
                    "应补偿解冻2批（多边形外两批），实际=" + resp.path("data").path("releasedCount"));
            check(resp.path("data").path("skippedStillBoundCount").asInt() == 1,
                    "应有1批（多边形内）仍被R1冻结而挂起补偿，实际="
                            + resp.path("data").path("skippedStillBoundCount"));

            NoCultureCompensation edgeComp = compensationMapper.selectOne(new QueryWrapper<NoCultureCompensation>()
                    .eq("rule_id", r3).eq("batch_id", batchEdge));
            check(edgeComp != null && NoCultureCompensation.RESULT_RELEASED.equals(edgeComp.getResult()),
                    "多边形外批补偿应为RELEASED");
            NoCultureCompensation inComp = compensationMapper.selectOne(new QueryWrapper<NoCultureCompensation>()
                    .eq("rule_id", r3).eq("batch_id", batchIn));
            check(inComp != null && NoCultureCompensation.RESULT_SKIPPED_STILL_BOUND.equals(inComp.getResult()),
                    "多边形内批补偿应为SKIPPED_STILL_BOUND");

            // 冻结账：R3三行都已RELEASED；batchIn 仍有R1的FROZEN行
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r3).eq("status", NoCultureFreeze.STATUS_RELEASED)) == 3L,
                    "R3三条冻结账自身都应置RELEASED");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", batchIn).eq("status", NoCultureFreeze.STATUS_FROZEN)) == 1L,
                    "多边形内批应仍被R1冻结1条");

            // 部分成功下操作结果分化：边界外批放行、重叠批仍拦
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + batchEdge + "/harvest",
                    harvestBody("2026-10-07", 1000))), "R3撤销后多边形外批应可捕捞");
            JsonNode stillBlocked = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchIn + "/harvest",
                    harvestBody("2026-10-07", 1000)));
            check(!stillBlocked.path("success").asBoolean(true), "仍被R1冻结的批次应继续拦截捕捞");

            // 勾稽：禁养区域R3 → 空间匹配 → 冻结账 → 补偿逐条对应，无悬挂
            JsonNode reconcile = json(call(HttpMethod.GET,
                    "/api/aqua/noculture/rules/" + r3 + "/reconcile", null));
            assertSuccess(reconcile, "R3勾稽核对");
            check(reconcile.path("data").path("balanced").asBoolean(),
                    "R3补偿与空间匹配应账账对应（balanced），实际=" + reconcile.path("data"));

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/noculture/rules/" + r3 + "/revoke",
                    revokeBody("监管李海", "文件到期撤销R3", "nc-r3-rev")));
            assertSuccess(again, "重复撤销R3");
            check(again.path("data").path("idempotent").asBoolean(), "重复撤销应幂等");
            check(compensationMapper.selectCount(new QueryWrapper<NoCultureCompensation>().eq("rule_id", r3)) == 3L,
                    "重复撤销不得新增补偿记录");
        });

        checkpoint("CP9", "撤销R1后多边形内批彻底解冻并可捕捞；R1勾稽平衡", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules/" + r1 + "/revoke",
                    revokeBody("监管钱海", "规划调整撤销R1", "nc-r1-rev")));
            assertSuccess(resp, "撤销R1");
            check(resp.path("data").path("releasedCount").asInt() == 1
                            && resp.path("data").path("skippedStillBoundCount").asInt() == 0,
                    "R1撤销后其冻结批应全部RELEASED，无挂起");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", batchIn).eq("status", NoCultureFreeze.STATUS_FROZEN)) == 0L,
                    "多边形内批不应再有任何FROZEN账");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + batchIn + "/harvest",
                    harvestBody("2026-10-08", 1000))), "全部规则撤销后多边形内批应可捕捞");

            JsonNode reconcile = json(call(HttpMethod.GET,
                    "/api/aqua/noculture/rules/" + r1 + "/reconcile", null));
            check(reconcile.path("data").path("balanced").asBoolean(),
                    "R1补偿与空间匹配应账账对应（balanced）");
        });

        checkpoint("CP10", "全程留证：规则登记/撤销、冻结/解冻/挂起/已完成提示证据齐全且均带操作者，可按对象追溯", () -> {
            check(evidenceCount(NoCultureChangeEvidence.ACTION_CREATE_RULE, null) == 3, "应有3条规则登记证据");
            check(evidenceCount(NoCultureChangeEvidence.ACTION_REVOKE_RULE, null) == 2, "应有2条规则撤销证据");
            check(evidenceCount(NoCultureChangeEvidence.ACTION_FREEZE, null) == 4,
                    "应有4条冻结证据（R1冻1、R3冻3；R2登记时时间湾无在途批）");
            check(evidenceCount(NoCultureChangeEvidence.ACTION_COMPLETED_NOTICE, null) == 2,
                    "已完成批应在R1/R3各留1条提示证据，共2条");
            check(evidenceCount(NoCultureChangeEvidence.ACTION_UNFREEZE, null) == 3,
                    "R3放多边形外2批、R1放重叠批应共3条解冻证据");
            check(evidenceCount(NoCultureChangeEvidence.ACTION_SKIP_UNFREEZE, null) == 1,
                    "R3撤销时重叠批应留1条挂起（未解冻）证据");
            List<NoCultureChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            check(!all.isEmpty() && all.stream().allMatch(e -> e.getOperator() != null
                            && !e.getOperator().trim().isEmpty()),
                    "每条证据都必须带操作者，不得丢操作者");

            // 已完成批全程只提示、无冻结、台账未回写
            check(matchMapper.selectCount(new QueryWrapper<NoCultureMatch>()
                    .eq("batch_id", batchDone)
                    .eq("result", NoCultureMatch.RESULT_COMPLETED_NOTICE)) == 2L,
                    "已完成批应有R1/R3两条只提示匹配");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", batchDone)) == 0L, "已完成批不得有任何冻结账");
            check(SeedlingBatch.STATUS_HARVESTED.equals(batchMapper.selectById(batchDone).getStatus()),
                    "已完成批台账始终为HARVESTED，未被回写");

            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/noculture/evidence?entityType=RULE&entityId=" + r1, null));
            assertSuccess(resp, "按对象查询R1证据");
            check(resp.path("data").size() >= 2, "R1维度应至少查到登记+撤销证据，实际="
                    + resp.path("data").size());
        });

        report.add("验收结论：全部检查点通过——禁养范围/生效时间/依据文件受控维护，投苗转池捕捞三入口空间与时间校验，规则新增冻结在途批且已完成只提示不回写，空间重叠按更严格规则执行，撤销部分解冻且补偿与空间匹配账账对应，冻结解冻全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name, String seaArea, String lon, String lat) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", seaArea);
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        body.put("longitude", lon);
        body.put("latitude", lat);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockBatch(String batchNo, Long pondId, String stockDate) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", stockBody(batchNo, pondId, stockDate)));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> stockBody(String batchNo, Long pondId, String stockDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", "LARGE_YELLOW_CROAKER");
        body.put("seedSource", "东山湾苗种场");
        body.put("quantity", 1000);
        body.put("stockDate", stockDate);
        body.put("plannedHarvestDate", "2027-12-31");
        return body;
    }

    private Map<String, Object> harvestBody(String date, int qty) {
        Map<String, Object> body = new HashMap<>();
        body.put("harvestDate", date);
        body.put("quantity", qty);
        return body;
    }

    private Map<String, Object> ruleBody(String ruleNo, String seaArea, String scopeType,
                                         List<List<Double>> polygonPoints, String from, String to,
                                         int strictLevel, String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("ruleNo", ruleNo);
        if (seaArea != null) {
            body.put("seaArea", seaArea);
        }
        body.put("scopeType", scopeType);
        if (polygonPoints != null) {
            body.put("polygon", polygonPoints);
        }
        body.put("effectiveFrom", from);
        if (to != null) {
            body.put("effectiveTo", to);
        }
        body.put("basisDocNo", "DOCK-2026-" + ruleNo);
        body.put("basisDocName", "海区禁养划定文件");
        body.put("strictLevel", strictLevel);
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> revokeBody(String operator, String reason, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("reason", reason);
        body.put("requestId", requestId);
        return body;
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<NoCultureChangeEvidence> wrapper = new QueryWrapper<NoCultureChangeEvidence>()
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
        sb.append("\n========== 海区禁养规则验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==========================================\n");
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
