package com.evops.aqua.release;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.InspectionRecordMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.release.entity.LegacyPlayback;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.entity.ReleaseOrder;
import com.evops.aqua.release.entity.ReleaseReport;
import com.evops.aqua.release.entity.ReleaseRound;
import com.evops.aqua.release.entity.ReleaseRuleVersion;
import com.evops.aqua.release.entity.ReleaseShipment;
import com.evops.aqua.release.entity.ReleaseSigner;
import com.evops.aqua.release.mapper.LegacyPlaybackMapper;
import com.evops.aqua.release.mapper.ReleaseChangeEvidenceMapper;
import com.evops.aqua.release.mapper.ReleaseOrderMapper;
import com.evops.aqua.release.mapper.ReleaseReportMapper;
import com.evops.aqua.release.mapper.ReleaseRoundMapper;
import com.evops.aqua.release.mapper.ReleaseRuleVersionMapper;
import com.evops.aqua.release.mapper.ReleaseShipmentMapper;
import com.evops.aqua.release.mapper.ReleaseSignerMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 批次出池检验与放行签名验收（aqua-13）。
 * 主线：先造海水养殖旧台账数据 → 用旧数据只读回放（差异可解释）→ 发布新品种+用途阈值版本
 * → 建检验单冻结版本 → 实验室录入、系统比对阈值生成放行建议（不合格 FAIL）
 * → 未签名不能发货、FAIL 不能签名 → 退回补检（保留初检结论/原因/时间，复检不覆盖初检）
 * → 复检合格 PASS → 样品编号与检测报告一一对应 → 仅授权签字人电子签名 → 签名后方可发货
 * → 发布 V2 旧版本停用且已建单冻结 V1 → 发布后回放差异仍可解释 → 全程留证、旧数据不变。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatchReleaseSignAcceptanceTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SeedlingBatchMapper batchMapper;
    @Autowired private InspectionRecordMapper inspectionMapper;
    @Autowired private ReleaseRuleVersionMapper versionMapper;
    @Autowired private ReleaseOrderMapper orderMapper;
    @Autowired private ReleaseRoundMapper roundMapper;
    @Autowired private ReleaseReportMapper reportMapper;
    @Autowired private ReleaseSignerMapper signerMapper;
    @Autowired private ReleaseShipmentMapper shipmentMapper;
    @Autowired private LegacyPlaybackMapper playbackMapper;
    @Autowired private ReleaseChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId;
    private Long stockedBatchId;
    private Long harvestedBatchId;
    private Long versionV1;
    private Long versionV2;
    private Long orderId;

    @Test
    void acceptanceBatchReleaseAndSignature() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = mapOf("username", "bootstrap", "password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "造海水养殖旧台账数据：1个在养大黄鱼批次（含旧抽检）+1个已出池对虾批次", () -> {
            pondId = createPond("REL-P01", "出池检验1号池");
            stockedBatchId = stockBatch("REL-B-STOCK", pondId, "大黄鱼", "2026-06-01", "2026-10-20");
            Map<String, Object> ins = new HashMap<>();
            ins.put("pondId", pondId);
            ins.put("batchId", stockedBatchId);
            ins.put("inspectDate", "2026-09-20");
            ins.put("sampleCount", 30);
            ins.put("avgWeightG", "420.50");
            ins.put("survivalRate", "92.30");
            ins.put("result", "长势良好，建议继续观察");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/records/inspection", ins)), "登记旧抽检");

            harvestedBatchId = stockBatch("REL-B-DONE", pondId, "南美白对虾", "2026-03-01", "2026-09-30");
            Map<String, Object> harvest = new HashMap<>();
            harvest.put("harvestDate", "2026-09-28");
            harvest.put("quantity", 5000);
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/batches/" + harvestedBatchId + "/harvest", harvest)), "旧批次出池");
            check(SeedlingBatch.STATUS_HARVESTED.equals(batchMapper.selectById(harvestedBatchId).getStatus()),
                    "对虾批次应为已出池");
        });

        checkpoint("CP2", "发布新版本前先用旧数据回放：只读、计数正确、差异可解释、重复请求不产生第二条", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/legacy-playback",
                    replayBody("replay-001", null)));
            assertSuccess(resp, "旧数据回放");
            Long playbackId = resp.path("data").path("id").asLong();
            check(resp.path("data").path("batchCount").asInt() >= 2, "回放批次计数应≥2");
            check(resp.path("data").path("pondCount").asInt() >= 1, "回放池塘计数应≥1");
            check(resp.path("data").path("stockedCount").asInt() >= 1, "在养计数应≥1");
            check(resp.path("data").path("harvestedCount").asInt() >= 1, "已出池计数应≥1");
            check(resp.path("data").path("inspectionCount").asInt() >= 1, "旧抽检计数应≥1");
            String summary = resp.path("data").path("diffSummary").asText();
            check(summary.contains("D1") && summary.contains("电子签名"),
                    "差异摘要应覆盖检验口径与签名发货门：" + summary);
            String findings = resp.path("data").path("findingsJson").asText();
            check(findings.contains("用途") && findings.contains("阈值"),
                    "逐批差异应解释品种+用途阈值口径");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/release/legacy-playback",
                    replayBody("replay-001", null)));
            assertSuccess(again, "重复回放请求");
            check(again.path("data").path("id").asLong() == playbackId, "重复回放应返回首次记录");
            long rows = playbackMapper.selectCount(new QueryWrapper<LegacyPlayback>());
            check(rows == 1, "重复回放产生了第二条记录，实际=" + rows);
        });

        checkpoint("CP3", "发布阈值新版本V1：检验项目按品种+用途组合（大黄鱼生食/熟食）", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("versionNo", "REL-RULE-V1");
            body.put("remark", "出池检验阈值V1");
            List<Map<String, Object>> ts = list(
                    threshold("大黄鱼", "RAW", "生食", "DRUG", "药物残留", "μg/kg", "0", "100"),
                    threshold("大黄鱼", "RAW", "生食", "BACT", "菌落总数", "CFU/g", "0", "50000"),
                    threshold("大黄鱼", "COOKED", "熟食", "DRUG", "药物残留", "μg/kg", "0", "100"),
                    threshold("大黄鱼", "COOKED", "熟食", "BACT", "菌落总数", "CFU/g", "0", "100000"));
            body.put("thresholds", ts);
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/rule-versions", body));
            assertSuccess(resp, "发布阈值版本V1");
            versionV1 = resp.path("data").path("id").asLong();
            check(ReleaseRuleVersion.STATUS_ACTIVE.equals(versionMapper.selectById(versionV1).getStatus()),
                    "V1应为生效版本");
        });

        checkpoint("CP4", "未配置品种+用途组合不能建单（南美白对虾无阈值）；重复版本号被拦截", () -> {
            Map<String, Object> noCombo = orderBody(harvestedBatchId, "COOKED", "熟食", "SMP-X", null);
            JsonNode r1 = json(call(HttpMethod.POST, "/api/aqua/release/orders", noCombo));
            check(!r1.path("success").asBoolean(true), "无阈值组合应拒绝建单");
            check(r1.path("message").asText().contains("未配置"), "拒绝原因不符：" + r1.path("message").asText());

            Map<String, Object> dup = new HashMap<>();
            dup.put("versionNo", "REL-RULE-V1");
            dup.put("thresholds", list(threshold("大黄鱼", "COOKED", "熟食", "DRUG", "药物残留", "μg/kg", "0", "100")));
            JsonNode r2 = json(call(HttpMethod.POST, "/api/aqua/release/rule-versions", dup));
            check(!r2.path("success").asBoolean(true), "重复版本号应被拦截");
        });

        checkpoint("CP5", "为在养大黄鱼批次按熟食用途建检验单：冻结V1，开初检轮与样品，结果行按阈值快照", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/orders",
                    orderBody(stockedBatchId, "COOKED", "熟食", "SMP-FISH-001", "sampler-zhang")));
            assertSuccess(resp, "建检验单");
            orderId = resp.path("data").path("id").asLong();
            check(resp.path("data").path("orderNo").asText().startsWith("REL-B" + stockedBatchId),
                    "检验单号应按批次编号：" + resp.path("data").path("orderNo").asText());
            check(resp.path("data").path("ruleVersionId").asLong() == versionV1, "检验单应冻结V1");
            check(ReleaseOrder.STATUS_DRAFT.equals(resp.path("data").path("status").asText()), "新单应为DRAFT");

            JsonNode detail = orderDetail();
            check(detail.path("rounds").size() == 1, "建单后应只有初检1轮");
            JsonNode round1 = detail.path("rounds").get(0);
            check(round1.path("round").path("roundNo").asInt() == 1, "首轮轮次号应为1");
            check(round1.path("results").size() == 2, "初检应快照2个指标阈值");
            check("SMP-FISH-001".equals(round1.path("sample").path("sampleNo").asText()),
                    "初检样品编号不符");
            check(round1.path("report").isNull(), "提交前不应有检测报告");
        });

        checkpoint("CP6", "提交校验：未录入/必检未录全拒绝；录入后系统按阈值闭区间逐项判定", () -> {
            JsonNode empty = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/submit",
                    submitBody("lab-li", "submit-000")));
            check(!empty.path("success").asBoolean(true), "未录入任何结果应拒绝提交");

            JsonNode partial = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/results",
                    enterBody(list(resultInput("DRUG", "120")), "enter-drug")));
            assertSuccess(partial, "只录入药物残留");
            JsonNode miss = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/submit",
                    submitBody("lab-li", "submit-001")));
            check(!miss.path("success").asBoolean(true), "必检项菌落总数未录应拒绝提交");
            check(miss.path("message").asText().contains("必检"), "拒绝提示应指明必检：" + miss.path("message").asText());

            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/results",
                    enterBody(list(resultInput("BACT", "8000")), "enter-bact"))), "录入菌落总数");
            JsonNode detail = orderDetail();
            JsonNode r1Results = detail.path("rounds").get(0).path("results");
            boolean drugFail = false;
            boolean bactPass = false;
            for (JsonNode r : r1Results) {
                if ("DRUG".equals(r.path("indicatorCode").asText())) {
                    drugFail = r.path("qualifiedFlag").asBoolean();
                }
                if ("BACT".equals(r.path("indicatorCode").asText())) {
                    bactPass = r.path("qualifiedFlag").asBoolean();
                }
            }
            check(!drugFail, "药物残留120>100应判定不合格");
            check(bactPass, "菌落总数8000在熟食阈值内应合格");
        });

        checkpoint("CP7", "提交初检：系统生成放行建议FAIL并签发1:1报告；重复提交幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/submit",
                    submitBody("lab-li", "submit-002")));
            assertSuccess(resp, "提交初检");
            check(ReleaseOrder.STATUS_SUBMITTED.equals(resp.path("data").path("status").asText()), "提交后应为SUBMITTED");
            check(ReleaseOrder.CONCLUSION_FAIL.equals(resp.path("data").path("recommendation").asText()),
                    "有不合格项，放行建议应为FAIL");
            check(resp.path("data").path("recommendationDetail").asText().contains("不合格"),
                    "建议说明应列出不合格项");

            JsonNode detail = orderDetail();
            JsonNode round1 = detail.path("rounds").get(0);
            check(ReleaseRound.STATUS_SUBMITTED.equals(round1.path("round").path("status").asText()),
                    "初检轮应为SUBMITTED");
            check(!round1.path("report").isNull() && "FAIL".equals(round1.path("report").path("conclusion").asText()),
                    "初检应签发结论FAIL的检测报告");
            Long reports = reportMapper.selectCount(new QueryWrapper<ReleaseReport>().eq("order_id", orderId));
            check(reports == 1L, "初检后应恰好1份报告，实际=" + reports);

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/submit",
                    submitBody("lab-li", "submit-002")));
            assertSuccess(again, "重复提交");
            check(reportMapper.selectCount(new QueryWrapper<ReleaseReport>().eq("order_id", orderId)) == 1L,
                    "重复提交又签发了报告");
        });

        checkpoint("CP8", "未电子签名不能发货；FAIL结论不能电子签名", () -> {
            JsonNode ship = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/ship",
                    shipBody("2026-10-07", 1000, "发货员wang", "ship-001")));
            check(!ship.path("success").asBoolean(true), "未签名发货必须被拦截");
            check(ship.path("message").asText().contains("电子签名"), "拦截提示应指向签名：" + ship.path("message").asText());
            check(shipmentMapper.selectCount(new QueryWrapper<ReleaseShipment>()) == 0L, "被拒发货不应落库");

            registerSigner("EMP001", "授权陈总", "s3cr3t", true);
            JsonNode signFail = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/sign",
                    signBody("EMP001", "s3cr3t", "sign-fail-1", "初检不合格仍申请放行")));
            check(!signFail.path("success").asBoolean(true), "放行建议FAIL不应允许电子签名");
            check(signFail.path("message").asText().contains("FAIL"), "拒绝提示应说明结论FAIL");
            check(ReleaseOrder.STATUS_SUBMITTED.equals(orderMapper.selectById(orderId).getStatus()),
                    "被拒签名后单据应仍为SUBMITTED");
        });

        checkpoint("CP9", "退回补检：保留初检结论/原因/操作时间，另开第2轮，复检不覆盖初检", () -> {
            Map<String, Object> ret = new HashMap<>();
            ret.put("reason", "药物残留初检超标，退回重新抽样检测");
            ret.put("operator", "qc-sun");
            ret.put("requestId", "return-001");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/return", ret));
            assertSuccess(resp, "退回补检");
            check(ReleaseOrder.STATUS_RETURNED.equals(resp.path("data").path("status").asText()),
                    "退回后单据应为RETURNED");
            check(resp.path("data").path("currentRoundNo").asInt() == 2, "应新开第2复检轮");

            JsonNode detail = orderDetail();
            check(detail.path("rounds").size() == 2, "应有初检+复检2轮");
            JsonNode round1 = detail.path("rounds").get(0);
            check(ReleaseRound.STATUS_RETURNED.equals(round1.path("round").path("status").asText()),
                    "初检轮状态应保留为RETURNED");
            check("FAIL".equals(round1.path("round").path("conclusion").asText()),
                    "初检原结论FAIL必须保留");
            check(round1.path("round").path("returnReason").asText().contains("药物残留"),
                    "初检轮必须保留退回原因");
            check(!round1.path("round").path("submittedTime").isNull()
                    && !round1.path("round").path("returnedTime").isNull(), "初检轮必须保留提交/退回操作时间");
            check("FAIL".equals(round1.path("report").path("conclusion").asText()),
                    "初检报告结论FAIL必须原样保留");
            JsonNode round2 = detail.path("rounds").get(1);
            check(round2.path("round").path("roundNo").asInt() == 2
                    && "DRAFT".equals(round2.path("round").path("status").asText()), "复检轮应为第2轮DRAFT");
            check(round2.path("report").isNull(), "复检轮提交前不应有报告");
        });

        checkpoint("CP10", "复检录入合格值并提交PASS：初检数据原样不被覆盖", () -> {
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/results",
                    enterBody(list(resultInput("DRUG", "80"), resultInput("BACT", "6000")), "enter-r2"))),
                    "复检录入合格值");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/submit",
                    submitBody("lab-li", "submit-r2")));
            assertSuccess(resp, "提交复检");
            check(ReleaseOrder.STATUS_SUBMITTED.equals(resp.path("data").path("status").asText()),
                    "复检提交后单据回到SUBMITTED");
            check(ReleaseOrder.CONCLUSION_PASS.equals(resp.path("data").path("recommendation").asText()),
                    "复检全部合格，放行建议应为PASS");

            JsonNode detail = orderDetail();
            JsonNode r1 = detail.path("rounds").get(0);
            JsonNode r2 = detail.path("rounds").get(1);
            check("FAIL".equals(r1.path("round").path("conclusion").asText()), "初检结论仍须为FAIL（未被复检覆盖）");
            String r1Drug = null;
            for (JsonNode r : r1.path("results")) {
                if ("DRUG".equals(r.path("indicatorCode").asText())) {
                    r1Drug = r.path("valueText").asText();
                }
            }
            check("120".equals(r1Drug), "初检药物残留实测值必须保留120，实际=" + r1Drug);
            check("PASS".equals(r2.path("round").path("conclusion").asText()), "复检结论应为PASS");
            check("PASS".equals(r2.path("report").path("conclusion").asText()), "复检报告应为PASS");
        });

        checkpoint("CP11", "样品编号与检测报告严格一一对应（2样品各1报告、编号各异）", () -> {
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/release/orders/" + orderId + "/sample-report-mapping", null));
            assertSuccess(resp, "样品报告对应核对");
            check(resp.path("data").path("sampleCount").asInt() == 2, "应有2个样品（初检+复检）");
            check(resp.path("data").path("oneToOne").asBoolean(), "样品与报告必须1:1");
            List<String> sampleNos = new ArrayList<>();
            List<String> reportNos = new ArrayList<>();
            for (JsonNode row : resp.path("data").path("rows")) {
                check(row.path("reportCount").asInt() == 1, "每个样品必须恰好1份报告");
                sampleNos.add(row.path("sampleNo").asText());
                reportNos.add(row.path("reportNo").asText());
            }
            check(sampleNos.size() == 2 && !sampleNos.get(0).equals(sampleNos.get(1)), "样品编号必须唯一不同");
            check(reportNos.size() == 2 && !reportNos.get(0).equals(reportNos.get(1)), "报告编号必须唯一不同");
        });

        checkpoint("CP12", "电子签名授权控制：未知工号/错口令/停用均拒绝，恢复授权后方可签名", () -> {
            JsonNode unknown = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/sign",
                    signBody("EMP999", "x", "sign-fail-2", null)));
            check(!unknown.path("success").asBoolean(true), "未知工号应拒绝签名");

            JsonNode wrongSecret = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/sign",
                    signBody("EMP001", "wrong", "sign-fail-3", null)));
            check(!wrongSecret.path("success").asBoolean(true), "签名口令错误应拒绝");

            JsonNode deauth = json(call(HttpMethod.POST,
                    "/api/aqua/release/signers/EMP001/authorization?authorized=false&reason=休假暂停",
                    null));
            assertSuccess(deauth, "停用签字人");
            JsonNode stopped = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/sign",
                    signBody("EMP001", "s3cr3t", "sign-fail-4", null)));
            check(!stopped.path("success").asBoolean(true), "已停用签字人应拒绝签名");

            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/release/signers/EMP001/authorization?authorized=true&reason=返岗恢复",
                    null)), "恢复授权");

            JsonNode ok = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/sign",
                    signBody("EMP001", "s3cr3t", "sign-ok", "复检合格，同意放行")));
            assertSuccess(ok, "授权电子签名");
            check(ReleaseOrder.STATUS_SIGNED.equals(ok.path("data").path("status").asText()),
                    "签名后单据应为SIGNED");
            check("EMP001".equals(ok.path("data").path("signerEmployeeNo").asText()), "签字人工号应落库");
            check(ok.path("data").path("signatureNo").asText().startsWith("SIG-"), "应生成签名号");
            check(!ok.path("data").path("signedTime").isNull(), "签名时间必须落库");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/sign",
                    signBody("EMP001", "s3cr3t", "sign-ok", "重复签名")));
            assertSuccess(again, "重复签名幂等");
            check(ReleaseOrder.STATUS_SIGNED.equals(again.path("data").path("status").asText()),
                    "重复签名后应仍为SIGNED（未发货前）");
            check(evidenceCount(ReleaseChangeEvidence.ACTION_SIGN, orderId) == 1,
                    "签名证据应只有1条，实际=" + evidenceCount(ReleaseChangeEvidence.ACTION_SIGN, orderId));
        });

        checkpoint("CP13", "签名后批次方可发货；一单发货一次、requestId幂等，单据置SHIPPED", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/ship",
                    shipBody("2026-10-08", 1000, "发货员wang", "ship-ok-1")));
            assertSuccess(resp, "签名后发货");
            Long shipmentId = resp.path("data").path("id").asLong();
            check(("SHP-O" + orderId).equals(resp.path("data").path("shipmentNo").asText()),
                    "发货单号应为SHP-O+检验单id：" + resp.path("data").path("shipmentNo").asText());
            check(ReleaseOrder.STATUS_SHIPPED.equals(orderMapper.selectById(orderId).getStatus()),
                    "发货后单据应为SHIPPED");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/ship",
                    shipBody("2026-10-08", 1000, "发货员wang", "ship-ok-1")));
            assertSuccess(again, "同requestId重复发货");
            check(again.path("data").path("id").asLong() == shipmentId, "重复发货应返回首次记录");
            JsonNode other = json(call(HttpMethod.POST, "/api/aqua/release/orders/" + orderId + "/ship",
                    shipBody("2026-10-09", 2000, "发货员wang", "ship-ok-2")));
            assertSuccess(other, "换requestId重复发货仍幂等");
            check(other.path("data").path("id").asLong() == shipmentId, "一单只能发货一次");
            check(shipmentMapper.selectCount(new QueryWrapper<ReleaseShipment>().eq("order_id", orderId)) == 1L,
                    "发货记录应只有1条");
        });

        checkpoint("CP14", "发布V2旧版本停用，已发货检验单仍冻结V1阈值（初检100不被V2的80改写）", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("versionNo", "REL-RULE-V2");
            body.put("remark", "熟食药物残留阈值收紧到80");
            body.put("thresholds", list(
                    threshold("大黄鱼", "COOKED", "熟食", "DRUG", "药物残留", "μg/kg", "0", "80"),
                    threshold("大黄鱼", "COOKED", "熟食", "BACT", "菌落总数", "CFU/g", "0", "100000")));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/rule-versions", body));
            assertSuccess(resp, "发布V2");
            versionV2 = resp.path("data").path("id").asLong();
            check(ReleaseRuleVersion.STATUS_SUPERSEDED.equals(versionMapper.selectById(versionV1).getStatus()),
                    "发布V2后V1应停用");
            check(ReleaseRuleVersion.STATUS_ACTIVE.equals(versionMapper.selectById(versionV2).getStatus()),
                    "V2应为生效版本");
            check(orderMapper.selectById(orderId).getRuleVersionId().equals(versionV1),
                    "已建检验单必须继续冻结V1");
            // 初检结果行阈值快照仍为 V1 的 100，而非 V2 的 80
            JsonNode r1 = orderDetail().path("rounds").get(0);
            String drugMax = null;
            for (JsonNode r : r1.path("results")) {
                if ("DRUG".equals(r.path("indicatorCode").asText())) {
                    drugMax = r.path("thresholdMax").asText();
                }
            }
            check(drugMax != null && new java.math.BigDecimal(drugMax).compareTo(new java.math.BigDecimal("100")) == 0,
                    "初检阈值快照必须保持V1的100，实际=" + drugMax);
        });

        checkpoint("CP15", "发布V2后再回放：差异仍可解释、旧台账数据原样未改、全程留证", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/release/legacy-playback",
                    replayBody("replay-002", null)));
            assertSuccess(resp, "发布后回放");
            String findings = resp.path("data").path("findingsJson").asText();
            check(findings.contains("大黄鱼") && findings.contains("COOKED"),
                    "回放应解释大黄鱼可匹配的熟食用途组合");
            check(findings.contains("南美白对虾"), "回放应覆盖对虾批次");

            // 旧台账数据原样：旧抽检仍在、对虾仍已出池、回放未代建任何检验单
            Long ins = inspectionMapper.selectCount(new QueryWrapper<InspectionRecord>()
                    .eq("batch_id", stockedBatchId));
            check(ins == 1L, "旧抽检记录必须原样保留1条，实际=" + ins);
            check(SeedlingBatch.STATUS_HARVESTED.equals(batchMapper.selectById(harvestedBatchId).getStatus()),
                    "已出池旧批次状态不应被回放改写");
            check(orderMapper.selectCount(new QueryWrapper<ReleaseOrder>().eq("batch_id", stockedBatchId)) == 1L,
                    "回放不应代建检验单");

            // 全程留证
            check(evidenceMapper.selectCount(new QueryWrapper<ReleaseChangeEvidence>()
                    .eq("action", ReleaseChangeEvidence.ACTION_REPLAY)) >= 2L, "回放证据应≥2条");
            check(evidenceCount(ReleaseChangeEvidence.ACTION_RETURN, orderId) == 1, "退回补检证据应为1条");
            check(evidenceCount(ReleaseChangeEvidence.ACTION_SHIP, null) >= 1, "发货证据缺失");
            Long signEvidence = evidenceMapper.selectCount(new QueryWrapper<ReleaseChangeEvidence>()
                    .eq("entity_type", ReleaseChangeEvidence.ENTITY_ORDER)
                    .eq("entity_id", orderId)
                    .eq("action", ReleaseChangeEvidence.ACTION_SIGN));
            check(signEvidence == 1L, "电子签名证据应为1条，实际=" + signEvidence);
        });

        report.add("验收结论：全部检查点通过——旧数据只读回放且差异可解释、检验项目按品种+用途组合阈值、录入后系统比对阈值生成放行建议、复检不覆盖初检且退回保留原结论原因时间、样品与报告一一对应、仅授权签字人电子签名后批次方可发货、新旧版本冻结口径正确");
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

    private Long stockBatch(String batchNo, Long pondId, String species, String stockDate, String planDate)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", species);
        body.put("seedSource", "海区育苗场");
        body.put("quantity", 8000);
        body.put("stockDate", stockDate);
        body.put("plannedHarvestDate", planDate);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private void registerSigner(String empNo, String name, String secret, boolean authorized) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeNo", empNo);
        body.put("signerName", name);
        body.put("signSecret", secret);
        body.put("authorizedFlag", authorized);
        body.put("requestId", "signer-" + empNo);
        assertSuccess(json(call(HttpMethod.POST, "/api/aqua/release/signers", body)), "登记签字人" + empNo);
        ReleaseSigner s = signerMapper.selectOne(new QueryWrapper<ReleaseSigner>().eq("employee_no", empNo));
        check(s != null, "签字人应落库");
    }

    private Map<String, Object> threshold(String species, String usageCode, String usageName,
                                          String indCode, String indName, String unit, String min, String max) {
        Map<String, Object> m = new HashMap<>();
        m.put("speciesCode", species);
        m.put("speciesName", species);
        m.put("usageCode", usageCode);
        m.put("usageName", usageName);
        m.put("indicatorCode", indCode);
        m.put("indicatorName", indName);
        m.put("unit", unit);
        m.put("minValue", min);
        m.put("maxValue", max);
        m.put("requiredFlag", true);
        return m;
    }

    private Map<String, Object> orderBody(Long batchId, String usageCode, String usageName,
                                          String sampleNo, String sampler) {
        Map<String, Object> m = new HashMap<>();
        m.put("batchId", batchId);
        m.put("usageCode", usageCode);
        m.put("usageName", usageName);
        m.put("sampleNo", sampleNo);
        m.put("sampler", sampler);
        m.put("requestId", "order-" + batchId + "-" + usageCode + "-" + System.nanoTime());
        return m;
    }

    private Map<String, Object> resultInput(String code, String value) {
        Map<String, Object> m = new HashMap<>();
        m.put("indicatorCode", code);
        m.put("valueText", value);
        return m;
    }

    private Map<String, Object> enterBody(List<Map<String, Object>> items, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("items", items);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> submitBody(String by, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("submittedBy", by);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> signBody(String empNo, String secret, String requestId, String remark) {
        Map<String, Object> m = new HashMap<>();
        m.put("employeeNo", empNo);
        m.put("signSecret", secret);
        m.put("requestId", requestId);
        m.put("remark", remark);
        return m;
    }

    private Map<String, Object> shipBody(String date, int qty, String operator, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("shipDate", date);
        m.put("quantity", qty);
        m.put("consignee", "城发水产品市场");
        m.put("operator", operator);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> replayBody(String requestId, Long batchId) {
        Map<String, Object> m = new HashMap<>();
        m.put("requestId", requestId);
        m.put("batchId", batchId);
        m.put("operator", "replay-bot");
        return m;
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
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

    private JsonNode orderDetail() throws Exception {
        return json(call(HttpMethod.GET, "/api/aqua/release/orders/" + orderId, null)).path("data");
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<ReleaseChangeEvidence> w = new QueryWrapper<ReleaseChangeEvidence>().eq("action", action);
        if (entityId != null) {
            w.eq("entity_id", entityId).eq("entity_type", ReleaseChangeEvidence.ENTITY_ORDER);
        }
        Long c = evidenceMapper.selectCount(w);
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
        sb.append("\n========== 批次出池检验与放行签名验收报告 ==========\n");
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
