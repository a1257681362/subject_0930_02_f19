package com.evops.aqua.growth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceCoefficient;
import com.evops.aqua.growth.entity.GrowthHarvestForecast;
import com.evops.aqua.growth.entity.GrowthMeasurementSample;
import com.evops.aqua.growth.entity.GrowthMeasurementTask;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.mapper.GrowthChangeEvidenceMapper;
import com.evops.aqua.growth.mapper.GrowthDeviceCoefficientMapper;
import com.evops.aqua.growth.mapper.GrowthHarvestForecastMapper;
import com.evops.aqua.growth.mapper.GrowthMeasurementSampleMapper;
import com.evops.aqua.growth.mapper.GrowthMeasurementTaskMapper;
import com.evops.aqua.growth.mapper.GrowthSamplingPlanMapper;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
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
 * 生长测量验收。
 * 主线：设备系数按版本保存（新版本停用旧版本）→ 按池塘+批次配置抽样数/频次/允许偏差并冻结系数版本
 * → 按方案生成测量任务（幂等）→ 现场录入体重长度按冻结系数校正求均值
 * → 超差样本不能静默删除：只能标记并填复核理由 → 测量员提交 → 主管驳回/确认后才更新预测出池量
 * → 发布新系数历史测量不重算 → 从测量记录反查原单、处理人及异常原因 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GrowthMeasurementAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private GrowthDeviceCoefficientMapper coeffMapper;
    @Autowired
    private GrowthSamplingPlanMapper planMapper;
    @Autowired
    private GrowthMeasurementTaskMapper taskMapper;
    @Autowired
    private GrowthMeasurementSampleMapper sampleMapper;
    @Autowired
    private GrowthHarvestForecastMapper forecastMapper;
    @Autowired
    private GrowthChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId;
    private Long batchId;
    private Long coeffV1;
    private Long coeffV2;
    private Long planId;
    private Long taskId1;

    private static final String DEVICE = "GW-SCALE-01";

    @Test
    void acceptanceGrowthMeasurement() {
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

        checkpoint("CP1", "建养殖池并投苗（一个批次）", () -> {
            Map<String, Object> pond = new HashMap<>();
            pond.put("pondCode", "GM-P01");
            pond.put("pondName", "生长测量1号池");
            pond.put("seaArea", "东山湾海区");
            pond.put("waterDepthM", "2.00");
            pond.put("waterAreaM2", "1000.00");
            pond.put("salinityPpt", "28.5");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", pond));
            assertSuccess(resp, "建池");
            pondId = resp.path("data").path("id").asLong();
            Pond row = pondMapper.selectById(pondId);
            check(row != null && "生长测量1号池".equals(row.getPondName()), "养殖池落库异常");

            Map<String, Object> batch = new HashMap<>();
            batch.put("batchNo", "GM-BATCH-001");
            batch.put("pondId", pondId);
            batch.put("species", "大黄鱼");
            batch.put("seedSource", "宁德官井洋育苗场");
            batch.put("quantity", 10000);
            batch.put("stockDate", "2026-09-01");
            batch.put("plannedHarvestDate", "2027-03-01");
            JsonNode bresp = json(call(HttpMethod.POST, "/api/aqua/batches", batch));
            assertSuccess(bresp, "投苗建批");
            batchId = bresp.path("data").path("id").asLong();
            check(SeedlingBatch.STATUS_STOCKED.equals(batchMapper.selectById(batchId).getStatus()),
                    "新批次应为在养状态");
        });

        checkpoint("CP2", "按设备保存校正系数版本V1（体重1.02/体长1.01），requestId幂等", () -> {
            Map<String, Object> body = coeffBody("COEF-V1", "1.0200", "1.0100", "2026-09-01", "coeff-v1");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/coefficients", body));
            assertSuccess(resp, "保存系数V1");
            coeffV1 = resp.path("data").path("id").asLong();
            check(GrowthDeviceCoefficient.STATUS_ACTIVE.equals(
                    coeffMapper.selectById(coeffV1).getStatus()), "V1应生效");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/growth/coefficients", body));
            assertSuccess(again, "重复保存系数V1");
            check(again.path("data").path("id").asLong() == coeffV1, "重复请求应返回首次版本");
            Long count = coeffMapper.selectCount(new QueryWrapper<GrowthDeviceCoefficient>()
                    .eq("device_code", DEVICE).eq("version_no", "COEF-V1"));
            check(count != null && count == 1L, "V1行数应为1，实际=" + count);
        });

        checkpoint("CP3", "按池塘+批次配置抽样数量5/7天一测/允许偏差10%，方案冻结V1", () -> {
            Map<String, Object> plan = new HashMap<>();
            plan.put("planNo", "GM-PLAN-001");
            plan.put("planName", "大黄鱼越冬前生长抽样");
            plan.put("pondId", pondId);
            plan.put("batchId", batchId);
            plan.put("sampleCount", 5);
            plan.put("measureIntervalDays", 7);
            plan.put("allowedDeviationPct", "10.00");
            plan.put("deviceCode", DEVICE);
            plan.put("startDate", "2026-10-01");
            plan.put("endDate", "2026-10-15");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/plans", plan));
            assertSuccess(resp, "创建抽样方案");
            planId = resp.path("data").path("id").asLong();
            GrowthSamplingPlan row = planMapper.selectById(planId);
            check(row.getSampleCount() == 5 && row.getMeasureIntervalDays() == 7,
                    "抽样数/频次落库异常");
            check(new BigDecimal("10.00").compareTo(row.getAllowedDeviationPct()) == 0,
                    "允许偏差落库异常：" + row.getAllowedDeviationPct());
            check(coeffV1.equals(row.getCoeffVersionId()), "方案必须冻结当时生效系数V1");
        });

        checkpoint("CP4", "按方案生成3个测量任务（10-01/08/15），重复生成不产生第二条", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/growth/plans/" + planId + "/tasks/generate", mapOf("requestId", "gm-gen-001")));
            assertSuccess(resp, "生成测量任务");
            check(resp.path("data").path("createdCount").asInt() == 3,
                    "应生成3个任务，实际=" + resp.path("data").path("createdCount").asInt());
            taskId1 = resp.path("data").path("created").get(0).path("id").asLong();
            GrowthMeasurementTask t = taskMapper.selectById(taskId1);
            check(t.getSampleCount() == 5, "任务应快照抽样数5");
            check(coeffV1.equals(t.getCoeffVersionId()), "任务应冻结系数版本V1");
            check(DEVICE.equals(t.getDeviceCode()), "任务应快照测量设备");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/growth/plans/" + planId + "/tasks/generate", mapOf("requestId", "gm-gen-001")));
            assertSuccess(again, "重复生成");
            check(again.path("data").path("createdCount").asInt() == 0, "重复生成新建应为0");
            check(again.path("data").path("existedCount").asInt() == 3, "重复生成应识别3条已有");
        });

        checkpoint("CP5", "保存系数V2后V1停用，但已生成任务仍冻结V1（历史不随新系数重算的前提）", () -> {
            Map<String, Object> body = coeffBody("COEF-V2", "1.0500", "1.0300", "2026-10-05", "coeff-v2");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/coefficients", body));
            assertSuccess(resp, "保存系数V2");
            coeffV2 = resp.path("data").path("id").asLong();
            check(GrowthDeviceCoefficient.STATUS_SUPERSEDED.equals(
                    coeffMapper.selectById(coeffV1).getStatus()), "V1应被停用");
            check(GrowthDeviceCoefficient.STATUS_ACTIVE.equals(
                    coeffMapper.selectById(coeffV2).getStatus()), "V2应生效");
            check(coeffV1.equals(taskMapper.selectById(taskId1).getCoeffVersionId()),
                    "已生成任务必须继续冻结V1");
        });

        checkpoint("CP6", "现场录入5尾体重长度，按V1校正并求均值（含一尾明显偏大的离群值）", () -> {
            Map<String, Object> req = recordBody("现场员阿海", 9800, "gm-record-001",
                    sample(1, "49", "100", false, null),
                    sample(2, "50", "100", false, null),
                    sample(3, "50", "100", false, null),
                    sample(4, "50", "100", false, null),
                    sample(5, "80", "100", false, null));
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/samples", req));
            assertSuccess(resp, "录入样本");
            GrowthMeasurementTask t = taskMapper.selectById(taskId1);
            // 原始均重 = (49+50+50+50+80)/5 = 55.8；此刻离群值尚未标记，仍计入
            check(new BigDecimal("55.800").compareTo(t.getRawAvgWeightG()) == 0,
                    "原始均重应为55.800：" + t.getRawAvgWeightG());
            check(new BigDecimal("56.916").compareTo(t.getCorrectedAvgWeightG()) == 0,
                    "按V1(1.02)校正均重应为56.916：" + t.getCorrectedAvgWeightG());
            check(new BigDecimal("101.000").compareTo(t.getCorrectedAvgLengthMm()) == 0,
                    "按V1(1.01)校正均长应为101.000：" + t.getCorrectedAvgLengthMm());
            check("现场员阿海".equals(t.getMeasuredBy()), "处理人应落任务单");
            GrowthMeasurementSample s1 = findSample(1);
            check(new BigDecimal("1.0200").compareTo(s1.getWeightCoeff()) == 0
                    && coeffV1.equals(s1.getCoeffVersionId()), "样本行应冻结V1系数");
            check(new BigDecimal("49.980").compareTo(s1.getCorrectedWeightG()) == 0,
                    "第1尾校正体重应为49.980：" + s1.getCorrectedWeightG());
        });

        checkpoint("CP7", "超差样本未标记不能提交；只能标记+填复核理由，不能静默删除", () -> {
            JsonNode rejected = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/submit", submitBody("gm-submit-001")));
            check(!rejected.path("success").asBoolean(true), "存在超差样本，提交应被拒绝");
            check(rejected.path("message").asText().contains("超过允许偏差"),
                    "应提示超过允许偏差：" + rejected.path("message").asText());
            check(GrowthMeasurementTask.STATUS_DRAFT.equals(taskMapper.selectById(taskId1).getStatus()),
                    "提交失败后任务应仍为草稿");

            // 标记异常但不填复核理由：必须拒绝
            Map<String, Object> noReason = new HashMap<>();
            noReason.put("abnormalityFlag", true);
            noReason.put("reviewedBy", "复核员阿珠");
            JsonNode flagNoReason = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/samples/5/review", noReason));
            check(!flagNoReason.path("success").asBoolean(true), "标记异常不填复核理由应被拒绝");

            // 正确标记异常 + 复核理由
            Map<String, Object> flag = new HashMap<>();
            flag.put("abnormalityFlag", true);
            flag.put("reviewReason", "该尾疑似外海窜入网箱的野杂鱼，体长正常但体重异常偏大，现场复核剔除");
            flag.put("reviewedBy", "复核员阿珠");
            flag.put("requestId", "gm-flag-005");
            JsonNode flagResp = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/samples/5/review", flag));
            assertSuccess(flagResp, "标记第5尾异常");

            // 异常行必须仍在（只标记不删除），且不计入均值
            List<GrowthMeasurementSample> rows = samplesOf(taskId1);
            check(rows.size() == 5, "异常样本不能被删除，样本行数应仍为5，实际=" + rows.size());
            GrowthMeasurementSample s5 = findSample(5);
            check(Boolean.TRUE.equals(s5.getAbnormalityFlag())
                    && Boolean.FALSE.equals(s5.getIncludedFlag()), "第5尾应标记异常并剔除均值");
            check(s5.getReviewReason().contains("野杂鱼"), "复核理由应落库");
            check("复核员阿珠".equals(s5.getReviewedBy()) && s5.getReviewedTime() != null,
                    "复核人与复核时间应落库");

            // 剔除后4尾：校正均重 = (49+50+50+50)/4*1.02 = 50.745
            GrowthMeasurementTask t = taskMapper.selectById(taskId1);
            check(new BigDecimal("50.745").compareTo(t.getCorrectedAvgWeightG()) == 0,
                    "剔除异常后校正均重应为50.745：" + t.getCorrectedAvgWeightG());

            // 已标记异常的样本不能被重新录入覆盖（防静默篡改/删除）
            Map<String, Object> overwrite = recordBody("现场员阿海", 9800, "gm-record-overwrite",
                    sample(5, "50", "100", false, null));
            JsonNode overResp = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/samples", overwrite));
            check(!overResp.path("success").asBoolean(true), "覆盖已标记异常样本应被拒绝");
            check(new BigDecimal("80.000").compareTo(findSample(5).getRawWeightG()) == 0,
                    "异常样本原始读数必须保持80，未被覆盖");

            // 此时提交应通过
            JsonNode submit = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/submit", submitBody("gm-submit-002")));
            assertSuccess(submit, "标记异常后提交");
            check(GrowthMeasurementTask.STATUS_SUBMITTED.equals(
                    submit.path("data").path("status").asText()), "提交后状态应为SUBMITTED");
        });

        checkpoint("CP8", "主管驳回须填意见且不产生预测；重新提交后确认才更新预测出池量", () -> {
            // 驳回无意见 → 拒绝
            Map<String, Object> rejectNoReason = confirmBody(false, "主管老陈", null, "gm-confirm-r0");
            JsonNode r0 = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/confirm", rejectNoReason));
            check(!r0.path("success").asBoolean(true), "驳回不填意见应被拒绝");

            // 驳回带意见 → REJECTED，不写预测
            JsonNode rejected = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/confirm",
                    confirmBody(false, "主管老陈", "复核照片角度不清，重新核体长", "gm-confirm-r1")));
            assertSuccess(rejected, "主管驳回");
            check(GrowthMeasurementTask.STATUS_REJECTED.equals(
                    rejected.path("data").path("status").asText()), "驳回后应为REJECTED");
            Long forecastCnt = forecastMapper.selectCount(new QueryWrapper<>());
            check(forecastCnt != null && forecastCnt == 0L, "驳回不能产生预测出池量，实际=" + forecastCnt);

            // 重新提交 → 主管确认通过
            JsonNode resubmit = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/submit", submitBody("gm-submit-003")));
            assertSuccess(resubmit, "驳回后重新提交");
            check(GrowthMeasurementTask.STATUS_SUBMITTED.equals(
                    resubmit.path("data").path("status").asText()), "重新提交后应回到SUBMITTED");

            JsonNode confirmed = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/confirm",
                    confirmBody(true, "主管老陈", "现场复核链完整，确认预测", "gm-confirm-c1")));
            assertSuccess(confirmed, "主管确认");
            check(GrowthMeasurementTask.STATUS_CONFIRMED.equals(
                    confirmed.path("data").path("status").asText()), "确认后应为CONFIRMED");

            // 预测出池量 = 校正均重50.745g × 在池9800尾 / 1000 = 497.301kg；存活率98%
            List<GrowthHarvestForecast> forecasts = forecastMapper.selectList(
                    new QueryWrapper<GrowthHarvestForecast>().eq("task_id", taskId1));
            check(forecasts.size() == 1, "确认后应只有1条预测，实际=" + forecasts.size());
            GrowthHarvestForecast f = forecasts.get(0);
            check(new BigDecimal("497.301").compareTo(f.getPredictedHarvestKg()) == 0,
                    "预测出池量应为497.301kg：" + f.getPredictedHarvestKg());
            check(new BigDecimal("98.00").compareTo(f.getSurvivalRatePct()) == 0,
                    "存活率应为98.00%：" + f.getSurvivalRatePct());
            check(coeffV1.equals(f.getCoeffVersionId()), "预测必须按任务冻结的V1口径");
            check("主管老陈".equals(f.getConfirmedBy()), "预测应记录确认主管");

            // 重复确认幂等，不产生第二条预测
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/confirm",
                    confirmBody(true, "主管老陈", "重复确认", "gm-confirm-c1")));
            assertSuccess(again, "重复确认请求");
            Long againCnt = forecastMapper.selectCount(
                    new QueryWrapper<GrowthHarvestForecast>().eq("task_id", taskId1));
            check(againCnt != null && againCnt == 1L, "重复确认又写了一条预测，实际=" + againCnt);
        });

        checkpoint("CP9", "发布V2后历史测量与预测不重算，且确认后任务冻结不可再录", () -> {
            GrowthMeasurementSample s1 = findSample(1);
            check(new BigDecimal("49.980").compareTo(s1.getCorrectedWeightG()) == 0
                    && coeffV1.equals(s1.getCoeffVersionId()),
                    "V2发布后历史样本校正值必须保持V1口径49.980");
            GrowthHarvestForecast f = forecastMapper.selectOne(
                    new QueryWrapper<GrowthHarvestForecast>().eq("task_id", taskId1));
            check(new BigDecimal("497.301").compareTo(f.getPredictedHarvestKg()) == 0,
                    "历史预测出池量不随新系数重算");

            Map<String, Object> rec = recordBody("现场员阿海", 9800, "gm-record-locked",
                    sample(1, "51", "101", false, null));
            JsonNode locked = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + taskId1 + "/samples", rec));
            check(!locked.path("success").asBoolean(true), "已主管确认任务必须冻结，不能再录入");
        });

        checkpoint("CP10", "方案下后续任务仍冻结方案当时的V1（系数版本不漂移到V2）", () -> {
            List<GrowthMeasurementTask> all = taskMapper.selectList(
                    new QueryWrapper<GrowthMeasurementTask>().eq("plan_id", planId).orderByAsc("measure_date"));
            check(all.size() == 3, "方案下应有3个任务");
            for (GrowthMeasurementTask t : all) {
                check(coeffV1.equals(t.getCoeffVersionId()),
                        "任务" + t.getTaskNo() + "必须冻结方案的V1，实际=" + t.getCoeffVersionId());
            }
        });

        checkpoint("CP11", "从测量记录反查抽样方案：定位原单、处理人及异常原因", () -> {
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/growth/reverse-trace?pondId=" + pondId + "&batchId=" + batchId, null));
            assertSuccess(resp, "反查抽样方案");
            check(resp.path("data").path("count").asInt() >= 1, "反查应至少定位1条测量记录");
            JsonNode rec = null;
            JsonNode records = resp.path("data").path("records");
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).path("taskId").asLong() == taskId1) {
                    rec = records.get(i);
                }
            }
            check(rec != null, "反查结果中应包含任务1");
            check("GM-PLAN-001".equals(rec.path("sourcePlan").path("planNo").asText()),
                    "应定位到原抽样方案单号GM-PLAN-001，实际="
                            + rec.path("sourcePlan").path("planNo").asText());
            check(rec.path("sourcePlan").path("sampleCount").asInt() == 5, "原方案抽样数应为5");
            check(rec.path("sourcePlan").path("allowedDeviationPct").asText().contains("10"),
                    "原方案允许偏差应为10%");
            String taskNo = rec.path("taskNo").asText();
            check(taskNo.startsWith("GM-"), "应定位原测量单号：" + taskNo);
            check("现场员阿海".equals(rec.path("handlers").path("measuredBy").asText()),
                    "应反查到现场处理人，实际=" + rec.path("handlers").path("measuredBy").asText());
            check("主管老陈".equals(rec.path("handlers").path("confirmedBy").asText()),
                    "应反查到确认主管，实际=" + rec.path("handlers").path("confirmedBy").asText());
            JsonNode abn = rec.path("abnormalSamples");
            check(abn.size() == 1 && abn.get(0).path("reviewReason").asText().contains("野杂鱼"),
                    "应反查到1尾异常样本及其复核原因，实际条数=" + abn.size());
            check("复核员阿珠".equals(abn.get(0).path("reviewedBy").asText()),
                    "应反查到异常复核人");

            // 按测量日精确反查
            JsonNode byDate = json(call(HttpMethod.GET,
                    "/api/aqua/growth/reverse-trace?pondId=" + pondId
                            + "&batchId=" + batchId + "&measureDate=2026-10-01", null));
            assertSuccess(byDate, "按日反查");
            check(byDate.path("data").path("count").asInt() == 1, "按测量日应只定位1条");
        });

        checkpoint("CP12", "所有写操作保留证据（系数停用/异常标记/提交/驳回/确认/生成）", () -> {
            check(countEvidence(GrowthChangeEvidence.ACTION_FLAG, null) >= 1, "异常标记证据缺失");
            check(countEvidence(GrowthChangeEvidence.ACTION_SUBMIT, null) >= 1, "提交证据缺失");
            check(countEvidence(GrowthChangeEvidence.ACTION_REJECT, null) == 1, "驳回证据应为1条");
            check(countEvidence(GrowthChangeEvidence.ACTION_CONFIRM, null) == 1, "确认证据应为1条");
            check(countEvidence(GrowthChangeEvidence.ACTION_GENERATE, null) == 3, "任务生成证据应为3条");
            // V1被V2停用的证据
            Long superseded = evidenceMapper.selectCount(new QueryWrapper<GrowthChangeEvidence>()
                    .eq("entity_type", GrowthChangeEvidence.ENTITY_COEFFICIENT)
                    .eq("entity_id", coeffV1)
                    .eq("action", GrowthChangeEvidence.ACTION_UPDATE));
            check(superseded != null && superseded == 1L, "旧系数版本停用证据缺失");
            // 证据里处理人不丢
            List<GrowthChangeEvidence> confirmEv = evidenceMapper.selectList(
                    new QueryWrapper<GrowthChangeEvidence>()
                            .eq("action", GrowthChangeEvidence.ACTION_CONFIRM));
            check(confirmEv.size() == 1 && "主管老陈".equals(confirmEv.get(0).getOperator()),
                    "确认证据必须保留主管处理人");
        });

        report.add("验收结论：全部检查点通过——按池塘批次配置抽样数/频次/偏差、按设备版本保存校正系数且历史不重算、"
                + "现场录入校正求均值、异常只标记不删除并填复核理由、主管确认后才更新预测出池量、测量记录可反查原单/处理人/异常原因、全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Map<String, Object> coeffBody(String versionNo, String w, String len, String date, String reqId) {
        Map<String, Object> m = new HashMap<>();
        m.put("deviceCode", DEVICE);
        m.put("deviceName", "秤尺一体测量仪01");
        m.put("versionNo", versionNo);
        m.put("weightCoeff", w);
        m.put("lengthCoeff", len);
        m.put("effectiveDate", date);
        m.put("requestId", reqId);
        return m;
    }

    private Map<String, Object> sample(int seq, String w, String len, boolean abnormal, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("seqNo", seq);
        m.put("rawWeightG", w);
        m.put("rawLengthMm", len);
        m.put("abnormalityFlag", abnormal);
        m.put("reviewReason", reason);
        return m;
    }

    private Map<String, Object> recordBody(String who, int total, String reqId, Map<String, Object>... samples) {
        Map<String, Object> m = new HashMap<>();
        m.put("measuredBy", who);
        m.put("totalCount", total);
        m.put("requestId", reqId);
        m.put("samples", list(samples));
        return m;
    }

    private Map<String, Object> submitBody(String reqId) {
        return mapOf("requestId", reqId);
    }

    private Map<String, Object> confirmBody(boolean approved, String who, String remark, String reqId) {
        Map<String, Object> m = new HashMap<>();
        m.put("approved", approved);
        m.put("confirmedBy", who);
        m.put("remark", remark);
        m.put("requestId", reqId);
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

    private List<GrowthMeasurementSample> samplesOf(Long taskId) {
        return sampleMapper.selectList(
                new QueryWrapper<GrowthMeasurementSample>().eq("task_id", taskId).orderByAsc("seq_no"));
    }

    private GrowthMeasurementSample findSample(int seq) {
        List<GrowthMeasurementSample> rows = sampleMapper.selectList(
                new QueryWrapper<GrowthMeasurementSample>().eq("task_id", taskId1).eq("seq_no", seq));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countEvidence(String action, Long entityId) {
        QueryWrapper<GrowthChangeEvidence> w = new QueryWrapper<GrowthChangeEvidence>().eq("action", action);
        if (entityId != null) {
            w.eq("entity_id", entityId);
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
        sb.append("\n========== 生长测量验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=======================================\n");
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
