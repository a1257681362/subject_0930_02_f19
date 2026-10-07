package com.evops.aqua.noculture;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.noculture.entity.NoCultureChangeEvidence;
import com.evops.aqua.noculture.entity.NoCultureFreeze;
import com.evops.aqua.noculture.entity.NoCultureMatch;
import com.evops.aqua.noculture.entity.NoCultureRule;
import com.evops.aqua.noculture.mapper.NoCultureChangeEvidenceMapper;
import com.evops.aqua.noculture.mapper.NoCultureFreezeMapper;
import com.evops.aqua.noculture.mapper.NoCultureMatchMapper;
import com.evops.aqua.noculture.mapper.NoCultureRuleMapper;
import com.evops.aqua.noculture.service.NoCultureSpatialMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * 海区禁养规则补充验收（aqua-11 修订）。
 * 针对线上缺陷"新增禁养范围后仍有在途转池单顺利提交"回归：
 *   1) 规则发布时间落库，冻结清单（匹配+冻结账）在发布事务内即生成；
 *   2) 边界相切按约定（相切即命中）——多边形边中点/顶点上的池塘必须冻结、转池拦截；
 *   3) 批次扫描显式按 STOCKED 条件取在途批，已完成（HARVESTED）只提示不回写台账；
 *   4) 补足跨日期（半开区间[from,to)端点）与多海区（多边形跨海区+各自整海区规则）重叠用例；
 *   5) 输入缺关键字段一次性指出全部缺项，补齐后只重试当前步骤即可成功；
 *   6) 空间边界判定异常时批次不静默放过，SPATIAL_FAILED 匹配与独立事务原始凭证必须保留。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NoCultureFreezeListAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private NoCultureRuleMapper ruleMapper;
    @Autowired
    private NoCultureMatchMapper matchMapper;
    @Autowired
    private NoCultureFreezeMapper freezeMapper;
    @Autowired
    private NoCultureChangeEvidenceMapper evidenceMapper;
    /** 最后一个检查点用于注入空间判定异常，验证异常下原始凭证保留。 */
    @SpyBean
    private NoCultureSpatialMatcher spatialMatcher;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondInside;   // 多边形严格内部
    private Long pondEdgeR;    // 多边形右边中点（相切）
    private Long pondEdgeC;    // 多边形右下顶点（相切）
    private Long pondEdgeB;    // 多边形底边中点（相切）
    private Long pondOutside;  // 多边形外、独立海区
    private Long pondTime;     // 跨日期用海区
    private Long pondP1;       // 几何在多边形内、归口甲海区
    private Long pondP2;       // 几何在多边形内、归口乙海区
    private Long pondPOutA;    // 甲海区但几何在多边形外

    private Long bInside, bEdgeR, bEdgeC, bEdgeB, bDone, bP1, bP2, bPOutA;

    private static final List<List<Double>> POLYGON = Arrays.asList(
            Arrays.asList(130.0, 20.0),
            Arrays.asList(130.3, 20.0),
            Arrays.asList(130.3, 20.3),
            Arrays.asList(130.0, 20.3));

    /** 第二多边形：独立海区、与R1/RP几何不重叠，专供异常凭证检查点使用。 */
    private static final List<List<Double>> POLYGON2 = Arrays.asList(
            Arrays.asList(125.0, 22.0),
            Arrays.asList(125.3, 22.0),
            Arrays.asList(125.3, 22.3),
            Arrays.asList(125.0, 22.3));

    @Test
    void acceptanceFreezeListOnPublish() {
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

        checkpoint("CP1", "建池：严格内部1口、边界相切3口（右边中点/右下顶点/底边中点）、区外1口、时间湾1口、跨海区重叠3口", () -> {
            pondInside = createPond("NC2-IN", "禁养A海区", "130.10", "20.10");
            pondEdgeR = createPond("NC2-ER", "禁养A海区", "130.30", "20.15");
            pondEdgeC = createPond("NC2-EC", "禁养A海区", "130.30", "20.00");
            pondEdgeB = createPond("NC2-EB", "禁养A海区", "130.15", "20.00");
            pondOutside = createPond("NC2-OUT", "外部海区", "121.90", "28.90");
            pondTime = createPond("NC2-TIME", "边界日期海区", "123.10", "24.00");
            pondP1 = createPond("NC2-P1", "重叠甲海区", "130.12", "20.12");
            pondP2 = createPond("NC2-P2", "重叠乙海区", "130.22", "20.22");
            pondPOutA = createPond("NC2-POA", "重叠甲海区", "121.90", "28.90");
        });

        checkpoint("CP2", "发布前布局在途批（含跨海区两口）与一口边界上的已完成批", () -> {
            bInside = stockBatch("NC2-C-IN", pondInside);
            bEdgeR = stockBatch("NC2-C-ER", pondEdgeR);
            bEdgeC = stockBatch("NC2-C-EC", pondEdgeC);
            bEdgeB = stockBatch("NC2-C-EB", pondEdgeB);
            bP1 = stockBatch("NC2-C-P1", pondP1);
            bP2 = stockBatch("NC2-C-P2", pondP2);
            bPOutA = stockBatch("NC2-C-POA", pondPOutA);
            Long done = stockBatch("NC2-C-DONE", pondEdgeR);
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + done + "/harvest",
                    harvestBody("2026-10-01", 1000))), "边界批在规则发布前完成捕捞");
            bDone = done;
        });

        checkpoint("CP3", "发布R1（长期、严格级1）：发布时间落库；冻结清单在发布事务内生成，边界相切3口与内部/跨海区共6个在途批全部冻结", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-R1", "禁养A海区", "POLYGON", POLYGON,
                            "2000-01-01", null, 1, "监管周海", "nc2-r1")));
            assertSuccess(resp, "发布多边形规则R1");
            Long r1 = resp.path("data").path("rule").path("id").asLong();

            NoCultureRule stored = ruleMapper.selectById(r1);
            check(stored.getPublishedTime() != null, "规则必须记录发布时间publishedTime");
            check(stored.getPublishedTime().toLocalDate().equals(LocalDate.now()),
                    "发布时间应为发布当日，实际=" + stored.getPublishedTime());

            check(resp.path("data").path("frozenCount").asInt() == 6,
                    "应冻结6个几何在多边形内的在途批（内部1+边界相切3+跨海区2），实际="
                            + resp.path("data").path("frozenCount"));
            for (Long batchId : Arrays.asList(bInside, bEdgeR, bEdgeC, bEdgeB, bP1, bP2)) {
                check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                        .eq("rule_id", r1).eq("batch_id", batchId)
                        .eq("status", NoCultureFreeze.STATUS_FROZEN)) == 1L,
                        "在途批id=" + batchId + "应在发布事务内进入冻结清单（含边界相切批）");
            }
            // 相切点在修复前会漏冻：几何在多边形外但同海区、甲海区外部位均不应冻
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r1).eq("batch_id", bPOutA)) == 0L, "多边形外批次不应冻结");
            // 冻结账时间与发布时间同一事务（同一日、且不晚于当前）
            NoCultureFreeze sample = freezeMapper.selectOne(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", r1).eq("batch_id", bEdgeR));
            check(sample != null && sample.getFrozenTime() != null
                            && !sample.getFrozenTime().toLocalDate().isBefore(stored.getPublishedTime().toLocalDate()),
                    "冻结账应在规则发布事务当时生成");

            // 已完成批：只提示、无冻结账、台账状态不回写
            check(resp.path("data").path("completedNoticeCount").asInt() == 1,
                    "已完成边界批应只提示1条");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", bDone)) == 0L, "已完成批不得有冻结账");
            check(SeedlingBatch.STATUS_HARVESTED.equals(batchMapper.selectById(bDone).getStatus()),
                    "已完成批台账必须保持HARVESTED，绝不回写");
            NoCultureMatch doneMatch = matchMapper.selectOne(new QueryWrapper<NoCultureMatch>()
                    .eq("rule_id", r1).eq("batch_id", bDone));
            check(doneMatch != null && NoCultureMatch.RESULT_COMPLETED_NOTICE.equals(doneMatch.getResult())
                            && doneMatch.getFreezeId() == null,
                    "已完成批只留COMPLETED_NOTICE且无冻结账");
        });

        checkpoint("CP4", "缺陷回归：规则发布后，边界相切在途批的转池单必须被拦截（修复前右边中点/顶点漏判为区外而提交成功）", () -> {
            for (Long boundaryBatch : Arrays.asList(bEdgeR, bEdgeC, bEdgeB, bInside)) {
                Map<String, Object> transfer = new HashMap<>();
                transfer.put("toPondId", pondOutside);
                transfer.put("transferDate", "2026-10-07");
                transfer.put("reason", "新增禁养范围后尝试转池");
                JsonNode tr = json(call(HttpMethod.POST,
                        "/api/aqua/batches/" + boundaryBatch + "/transfer", transfer));
                check(!tr.path("success").asBoolean(true) && tr.path("message").asText().contains("禁养"),
                        "边界/内部在途批id=" + boundaryBatch + "转池应被禁养规则拦截，实际："
                                + tr.path("message").asText());
                check(SeedlingBatch.STATUS_STOCKED.equals(batchMapper.selectById(boundaryBatch).getStatus()),
                        "被拦截的转池不得改动批次");
            }
        });

        checkpoint("CP5", "跨日期半开区间[from,to)：发布时窗口未结束故在途批入冻结清单；生效前/结束日放行，生效日/结束前一日拦截", () -> {
            Long td1 = stockBatch("NC2-T1", pondTime);
            Long td2 = stockBatch("NC2-T2", pondTime);
            Long td3 = stockBatch("NC2-T3", pondTime);
            Long td4 = stockBatch("NC2-T4", pondTime);

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-RT", "边界日期海区", "AREA", null,
                            "2026-01-01", "2026-12-31", 1, "监管吴海", "nc2-rt")));
            assertSuccess(resp, "发布有限期整海区规则RT");
            Long rt = resp.path("data").path("rule").path("id").asLong();
            check(resp.path("data").path("frozenCount").asInt() == 4,
                    "窗口在发布日仍开放，4个在途批都应入冻结清单，实际=" + resp.path("data").path("frozenCount"));

            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + td1 + "/transfer",
                    transferBody(pondOutside, "2025-12-31"))), "生效日前一天转池应放行");
            JsonNode fromDay = json(call(HttpMethod.POST, "/api/aqua/batches/" + td2 + "/transfer",
                    transferBody(pondOutside, "2026-01-01")));
            check(!fromDay.path("success").asBoolean(true) && fromDay.path("message").asText().contains("禁养"),
                    "生效开始日当天（闭端点from）转池应拦截：" + fromDay.path("message").asText());
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + td3 + "/transfer",
                    transferBody(pondOutside, "2026-12-31"))), "生效结束日当天（半开端点to）转池应放行");
            JsonNode beforeTo = json(call(HttpMethod.POST, "/api/aqua/batches/" + td4 + "/transfer",
                    transferBody(pondOutside, "2026-12-30")));
            check(!beforeTo.path("success").asBoolean(true), "结束日前一天仍在区间内，转池应拦截");
        });

        checkpoint("CP6", "多海区重叠：多边形跨甲/乙两海区，与各自整海区规则叠加；同点命中多条按strictLevel最严规则裁决，撤销只解冻无他规约束的批", () -> {
            JsonNode rp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-RP", "跨海区归口", "POLYGON", POLYGON,
                            "2000-01-01", null, 2, "监管郑海", "nc2-rp")));
            assertSuccess(rp, "发布跨海区多边形规则RP(严格2)");
            Long rpId = rp.path("data").path("rule").path("id").asLong();
            JsonNode ra = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-RA", "重叠甲海区", "AREA", null,
                            "2000-01-01", null, 5, "监管王海", "nc2-ra")));
            assertSuccess(ra, "发布甲海区规则RA(严格5)");
            Long raId = ra.path("data").path("rule").path("id").asLong();
            JsonNode rb = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-RB", "重叠乙海区", "AREA", null,
                            "2000-01-01", null, 8, "监管冯海", "nc2-rb")));
            assertSuccess(rb, "发布乙海区规则RB(严格8)");
            Long rbId = rb.path("data").path("rule").path("id").asLong();

            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", bP1).eq("status", NoCultureFreeze.STATUS_FROZEN)) == 3L,
                    "甲海区几何内批应同时被R1/RP/RA三条规则冻结");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", bP2).eq("status", NoCultureFreeze.STATUS_FROZEN)) == 3L,
                    "乙海区几何内批应同时被R1/RP/RB三条规则冻结");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", raId).eq("batch_id", bPOutA)) == 1L,
                    "几何在多边形外的甲海区批应只被RA冻结");

            JsonNode stockP1 = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC2-C-NEW-P1", pondP1)));
            check(!stockP1.path("success").asBoolean(true)
                            && stockP1.path("message").asText().contains("NC2-RA")
                            && stockP1.path("message").asText().contains("更严格"),
                    "甲海区重叠点应按最严规则RA裁决：" + stockP1.path("message").asText());
            JsonNode stockP2 = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC2-C-NEW-P2", pondP2)));
            check(!stockP2.path("success").asBoolean(true)
                            && stockP2.path("message").asText().contains("NC2-RB"),
                    "乙海区重叠点应按最严规则RB裁决：" + stockP2.path("message").asText());

            // 撤销RA：几何内批仍被R1/RP冻结→SKIPPED_STILL_BOUND；多边形外甲批无他规→RELEASED
            JsonNode revoke = json(call(HttpMethod.POST, "/api/aqua/noculture/rules/" + raId + "/revoke",
                    revokeBody("监管王海", "甲海区文件调整", "nc2-ra-rev")));
            assertSuccess(revoke, "撤销RA");
            check(revoke.path("data").path("releasedCount").asInt() == 1
                            && revoke.path("data").path("skippedStillBoundCount").asInt() == 1,
                    "应解冻1批（多边形外甲批）、挂起1批（几何内甲批仍被R1/RP冻结），实际="
                            + revoke.path("data"));
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + bPOutA + "/transfer",
                    transferBody(pondOutside, "2026-10-07"))), "RA撤销后多边形外甲批转池应放行");
            JsonNode still = json(call(HttpMethod.POST, "/api/aqua/batches",
                    stockBody("NC2-C-NEW-P1B", pondP1)));
            check(!still.path("success").asBoolean(true) && still.path("message").asText().contains("NC2-RP"),
                    "RA撤销后甲海区重叠点应仍被RP(严格2)拦截：" + still.path("message").asText());
            // 乙海区批完全不受RA撤销影响
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", bP2).eq("status", NoCultureFreeze.STATUS_FROZEN)) == 3L,
                    "乙海区批冻结账不应受甲海区规则撤销影响");
        });

        checkpoint("CP7", "输入缺关键字段一次性指出全部缺项；补齐后只重试当前步骤（规则发布）即成功，既有布局与冻结清单无副作用", () -> {
            Map<String, Object> missing = ruleBody("NC2-MISS", "补项海区", "AREA", null,
                    "2000-01-01", null, 1, "监管补海", "nc2-miss");
            missing.remove("ruleNo");
            missing.remove("operator");
            missing.remove("requestId");
            missing.remove("basisDocName");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules", missing));
            check(!resp.path("success").asBoolean(true), "多字段缺失应被拒绝");
            String msg = resp.path("message").asText();
            for (String field : Arrays.asList("[ruleNo]", "[operator]", "[requestId]", "[basisDocName]")) {
                check(msg.contains(field), "缺项提示必须逐字段指出" + field + "，实际：" + msg);
            }

            Map<String, Object> noArea = ruleBody("NC2-MISS", null, "AREA", null,
                    "2000-01-01", null, 1, "监管补海", "nc2-miss");
            JsonNode areaResp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules", noArea));
            check(!areaResp.path("success").asBoolean(true)
                            && areaResp.path("message").asText().contains("海区"),
                    "整海区规则缺海区应指出缺项：" + areaResp.path("message").asText());

            // 补齐缺项后只重试"规则发布"这一步：未重跑建池/投苗，既有批次不重复、直接发布成功
            long batchesBefore = batchMapper.selectCount(new QueryWrapper<>());
            JsonNode fixed = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-MISS", "补项海区", "AREA", null,
                            "2000-01-01", null, 1, "监管补海", "nc2-miss")));
            assertSuccess(fixed, "补齐缺项后重试当前步骤应成功");
            check(batchMapper.selectCount(new QueryWrapper<>()) == batchesBefore,
                    "重试只发生在规则发布步骤，不得重复建池/投苗等前序步骤");
        });

        checkpoint("CP8", "空间边界判定异常：疑点批次不静默放过，SPATIAL_FAILED匹配与独立事务原始凭证（规则/批次/池塘快照+异常）保留，其余批照常部分成功", () -> {
            // 独立海区+独立多边形，发布前先布两个在途批（一个疑点批、一个正常批）
            Long pondBad = createPond("NC2-BADP", "异常凭证海区", "125.10", "22.10");
            Long pondOk = createPond("NC2-OKP", "异常凭证海区", "125.20", "22.20");
            Long bBad = stockBatch("NC2-C-BAD", pondBad);
            Long bOk = stockBatch("NC2-C-OK", pondOk);
            Pond badPond = pondMapper.selectById(pondBad);
            doThrow(new IllegalStateException("模拟边界计算异常"))
                    .when(spatialMatcher).hits(any(NoCultureRule.class),
                            argThat((Pond p) -> p != null && p.getId().equals(badPond.getId())));

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/noculture/rules",
                    ruleBody("NC2-RBAD", "异常凭证海区", "POLYGON", POLYGON2,
                            "2000-01-01", null, 1, "监管证海", "nc2-rbad")));
            assertSuccess(resp, "单批空间判定异常不影响整单发布（部分成功）");
            check(resp.path("data").path("spatialFailedCount").asInt() == 1,
                    "应报告1个空间判定疑点批，实际=" + resp.path("data").path("spatialFailedCount"));

            NoCultureMatch badMatch = matchMapper.selectOne(new QueryWrapper<NoCultureMatch>()
                    .eq("batch_id", bBad).eq("result", NoCultureMatch.RESULT_SPATIAL_FAILED));
            check(badMatch != null, "疑点批必须留SPATIAL_FAILED匹配，不得静默当不命中");
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("batch_id", bBad)) == 0L, "未确认命中的疑点批不得先入冻结账");

            List<NoCultureChangeEvidence> vouchers = evidenceMapper.selectList(
                    new QueryWrapper<NoCultureChangeEvidence>()
                            .eq("action", NoCultureChangeEvidence.ACTION_SPATIAL_FAILED)
                            .eq("entity_id", badMatch.getId()));
            check(vaccouchersOk(vouchers, bBad),
                    "必须以独立事务保留原始凭证：含异常类型/信息与批次快照，且带操作者");

            // 同一发布事务内正常批照常冻结（部分成功）
            Long rbad = resp.path("data").path("rule").path("id").asLong();
            check(freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                    .eq("rule_id", rbad).eq("batch_id", bOk)
                    .eq("status", NoCultureFreeze.STATUS_FROZEN)) == 1L,
                    "空间异常批次之外的在途批应在同一发布事务内照常冻结（部分成功）");
        });

        report.add("验收结论：全部检查点通过——发布时间落库且冻结清单发布事务内生成，边界相切即命中，在途批显式按STOCKED扫描且已完成不回写，跨日期端点与多海区重叠裁决正确，缺项一次性指出且补齐只重试当前步骤，空间异常保留独立事务原始凭证");
        printReport();
    }

    private boolean vaccouchersOk(List<NoCultureChangeEvidence> vouchers, Long bBad) {
        if (vouchers == null || vouchers.size() != 1) {
            return false;
        }
        NoCultureChangeEvidence v = vouchers.get(0);
        SeedlingBatch batch = batchMapper.selectById(bBad);
        return v.getOperator() != null && !v.getOperator().trim().isEmpty()
                && v.getAfterJson() != null
                && v.getAfterJson().contains("模拟边界计算异常")
                && v.getAfterJson().contains("IllegalStateException")
                && v.getAfterJson().contains(batch.getBatchNo());
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String seaArea, String lon, String lat) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", code + "池");
        body.put("seaArea", seaArea);
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        body.put("longitude", lon);
        body.put("latitude", lat);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockBatch(String batchNo, Long pondId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", stockBody(batchNo, pondId)));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> stockBody(String batchNo, Long pondId) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", "LARGE_YELLOW_CROAKER");
        body.put("seedSource", "苗种场");
        body.put("quantity", 1000);
        body.put("stockDate", "2026-09-01");
        body.put("plannedHarvestDate", "2027-12-31");
        return body;
    }

    private Map<String, Object> harvestBody(String date, int qty) {
        Map<String, Object> body = new HashMap<>();
        body.put("harvestDate", date);
        body.put("quantity", qty);
        return body;
    }

    private Map<String, Object> transferBody(Long toPondId, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("toPondId", toPondId);
        body.put("transferDate", date);
        body.put("reason", "跨日期转池");
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
        sb.append("\n========== 海区禁养冻结清单补充验收报告 ==========\n");
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
