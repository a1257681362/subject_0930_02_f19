package com.evops.aqua.sensor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.entity.SensorQuarantine;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewTask;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.mapper.SensorChangeEvidenceMapper;
import com.evops.aqua.sensor.mapper.SensorDailyReportMapper;
import com.evops.aqua.sensor.mapper.SensorDeviceMapper;
import com.evops.aqua.sensor.mapper.SensorQuarantineMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewTaskMapper;
import com.evops.aqua.sensor.mapper.SensorRuleVersionMapper;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 池塘水质传感器接入验收。
 * 主线：发布规则版本 → 登记设备 → 离线批量上传（验签）→ 按设备时间与序号归并
 * → 重传/部分重叠不重复计入日报 → 异常读数进待复核队列 → 复核剔除/确认
 * → 日报审核锁定区间 → 迟到数据只能补充未审核区间 → 签名失败包隔离留原文
 * → 设备更换新旧关联 → 规则调整只生成新版本不改旧结论 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SensorIngestAcceptanceTest {

    private static final String SECRET_1 = "sensor-secret-key-01";
    private static final String SECRET_2 = "sensor-secret-key-02";
    private static final String DEVICE_1 = "WQ-SENSOR-01";
    private static final String DEVICE_2 = "WQ-SENSOR-02";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SensorDeviceMapper deviceMapper;
    @Autowired
    private SensorRuleVersionMapper ruleVersionMapper;
    @Autowired
    private SensorReadingMapper readingMapper;
    @Autowired
    private SensorReviewTaskMapper reviewTaskMapper;
    @Autowired
    private SensorDailyReportMapper dailyReportMapper;
    @Autowired
    private SensorQuarantineMapper quarantineMapper;
    @Autowired
    private SensorChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId;
    private Long versionV1;
    private Long deviceId1;
    private Long deviceId2;
    private Long reviewTaskDo;
    private Long reviewTaskTemp;
    private Long reportSalinity1001;

    @Test
    void acceptanceSensorIngest() {
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

        checkpoint("CP1", "建养殖池", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("pondCode", "SEN-P01");
            body.put("pondName", "传感器1号池");
            body.put("seaArea", "东山湾海区");
            body.put("waterDepthM", "1.80");
            body.put("salinityPpt", "28.5");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
            assertSuccess(resp, "建池");
            pondId = resp.path("data").path("id").asLong();
        });

        checkpoint("CP2", "发布归并规则版本V1（盐度25-32/溶氧5-9/温度18-30）", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                    ruleBody("SEN-RULE-V1", "25.0", "32.0", "5.0", "9.0", "18.0", "30.0")));
            assertSuccess(resp, "发布规则版本V1");
            versionV1 = resp.path("data").path("id").asLong();
            SensorRuleVersion row = ruleVersionMapper.selectById(versionV1);
            check(SensorRuleVersion.STATUS_ACTIVE.equals(row.getStatus()), "V1应为生效版本");
        });

        checkpoint("CP3", "登记设备（requestId幂等，重复登记返回首条）", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/devices",
                    deviceBody(DEVICE_1, "1号池水质传感器", SECRET_1, "dev-reg-001")));
            assertSuccess(resp, "登记设备");
            deviceId1 = resp.path("data").path("id").asLong();

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/sensor/devices",
                    deviceBody(DEVICE_1, "1号池水质传感器", SECRET_1, "dev-reg-001")));
            assertSuccess(again, "重复登记请求");
            check(again.path("data").path("id").asLong() == deviceId1, "重复登记应返回首次设备");
            Long count = deviceMapper.selectCount(
                    new QueryWrapper<SensorDevice>().eq("device_code", DEVICE_1));
            check(count != null && count == 1L, "设备编码应只有一行，实际=" + count);
        });

        checkpoint("CP4", "离线批量上传：验签通过，按设备时间与序号归并6条读数并生成日报", () -> {
            String raw = payload(
                    reading("2026-10-01T08:00:00", 1, "28.5", "6.2", "24.3"),
                    reading("2026-10-01T09:00:00", 2, "29.0", "6.5", "25.0"));
            JsonNode resp = upload(DEVICE_1, "up-001", raw, sign(SECRET_1, "up-001", raw));
            assertSuccess(resp, "批量上传");
            JsonNode data = resp.path("data");
            check("MERGED".equals(data.path("status").asText()), "批次应归并，实际=" + data.path("status").asText());
            check(data.path("totalCount").asInt() == 6, "总读数应为6，实际=" + data.path("totalCount").asInt());
            check(data.path("mergedCount").asInt() == 6, "新归并应为6，实际=" + data.path("mergedCount").asInt());
            check(data.path("abnormalCount").asInt() == 0, "不应有异常，实际=" + data.path("abnormalCount").asInt());

            List<SensorDailyReport> reports = dailyReports("2026-10-01");
            check(reports.size() == 3, "应生成3个指标日报，实际=" + reports.size());
            SensorDailyReport salinity = findReport(reports, "SALINITY");
            check(salinity.getSampleCount() == 2, "盐度日报样本数应为2，实际=" + salinity.getSampleCount());
            check(SensorDailyReport.STATUS_OPEN.equals(salinity.getStatus()), "日报应为未审核OPEN");
            check(salinity.getRuleVersionId().equals(versionV1), "日报应冻结V1规则版本");
            reportSalinity1001 = salinity.getId();
        });

        checkpoint("CP5", "同requestId整包重传：返回首次结果，读数与日报不重复", () -> {
            String raw = payload(
                    reading("2026-10-01T08:00:00", 1, "28.5", "6.2", "24.3"),
                    reading("2026-10-01T09:00:00", 2, "29.0", "6.5", "25.0"));
            JsonNode resp = upload(DEVICE_1, "up-001", raw, sign(SECRET_1, "up-001", raw));
            assertSuccess(resp, "整包重传");
            check(resp.path("data").path("replay").asBoolean(false), "重传应命中整包幂等replay");
            Long readingCount = readingMapper.selectCount(new QueryWrapper<>());
            check(readingCount != null && readingCount == 6L, "重传后读数应仍为6，实际=" + readingCount);
            SensorDailyReport salinity = dailyReportMapper.selectById(reportSalinity1001);
            check(salinity.getSampleCount() == 2, "重传后日报样本数应仍为2，实际=" + salinity.getSampleCount());
        });

        checkpoint("CP6", "新requestId部分重叠上传：重叠槽位去重，日报不重复计入", () -> {
            String raw = payload(
                    reading("2026-10-01T08:00:00", 1, "28.5", "6.2", "24.3"),
                    reading("2026-10-01T10:00:00", 3, "30.0", "7.0", "26.0"));
            JsonNode resp = upload(DEVICE_1, "up-002", raw, sign(SECRET_1, "up-002", raw));
            assertSuccess(resp, "部分重叠上传");
            JsonNode data = resp.path("data");
            check(data.path("duplicateCount").asInt() == 3,
                    "重叠3条应去重，实际=" + data.path("duplicateCount").asInt());
            check(data.path("mergedCount").asInt() == 3,
                    "新槽位应归并3条，实际=" + data.path("mergedCount").asInt());
            Long readingCount = readingMapper.selectCount(new QueryWrapper<>());
            check(readingCount != null && readingCount == 9L, "读数总数应为9，实际=" + readingCount);
            SensorDailyReport salinity = dailyReportMapper.selectById(reportSalinity1001);
            check(salinity.getSampleCount() == 3, "日报样本数应为3（不重复计入），实际=" + salinity.getSampleCount());
            check(salinity.getMaxValue().compareTo(new java.math.BigDecimal("30.0")) == 0,
                    "日报最大值应为30.0，实际=" + salinity.getMaxValue());
        });

        checkpoint("CP7", "异常读数进待复核队列，暂不计入日报", () -> {
            String raw = payload(
                    reading("2026-10-02T08:00:00", 1, "28.0", "3.0", "24.0"),
                    reading("2026-10-02T09:00:00", 2, "28.2", "6.0", "35.0"));
            JsonNode resp = upload(DEVICE_1, "up-003", raw, sign(SECRET_1, "up-003", raw));
            assertSuccess(resp, "含异常读数上传");
            check(resp.path("data").path("abnormalCount").asInt() == 2,
                    "应有2条异常，实际=" + resp.path("data").path("abnormalCount").asInt());

            List<SensorReviewTask> pending = reviewTaskMapper.selectList(
                    new QueryWrapper<SensorReviewTask>().eq("status", SensorReviewTask.STATUS_PENDING));
            check(pending.size() == 2, "待复核队列应有2条，实际=" + pending.size());
            for (SensorReviewTask task : pending) {
                if ("DO".equals(task.getMetricType())) { reviewTaskDo = task.getId(); }
                if ("TEMP".equals(task.getMetricType())) { reviewTaskTemp = task.getId(); }
            }
            check(reviewTaskDo != null && reviewTaskTemp != null, "溶氧与温度异常都应入队");

            List<SensorDailyReport> reports = dailyReports("2026-10-02");
            SensorDailyReport doReport = findReport(reports, "DO");
            SensorDailyReport tempReport = findReport(reports, "TEMP");
            check(doReport.getSampleCount() == 1, "溶氧日报应只计正常值1条，实际=" + doReport.getSampleCount());
            check(tempReport.getSampleCount() == 1, "温度日报应只计正常值1条，实际=" + tempReport.getSampleCount());
        });

        checkpoint("CP8", "复核：剔除永不计入日报，确认补入未审核日报，重复复核幂等", () -> {
            Map<String, Object> exclude = new HashMap<>();
            exclude.put("decision", "EXCLUDED");
            exclude.put("reviewedByName", "质检员小王");
            exclude.put("reviewRemark", "探头附着海草，溶氧读数不可信");
            exclude.put("requestId", "rv-001");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewTaskDo + "/resolve", exclude));
            assertSuccess(resp, "复核剔除");
            SensorDailyReport doReport = findReport(dailyReports("2026-10-02"), "DO");
            check(doReport.getSampleCount() == 1, "剔除后溶氧日报应仍为1条，实际=" + doReport.getSampleCount());

            Map<String, Object> confirm = new HashMap<>();
            confirm.put("decision", "CONFIRMED");
            confirm.put("reviewedByName", "质检员小王");
            confirm.put("reviewRemark", "当日午后高温属实，温度读数确认有效");
            confirm.put("requestId", "rv-002");
            resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewTaskTemp + "/resolve", confirm));
            assertSuccess(resp, "复核确认");
            SensorDailyReport tempReport = findReport(dailyReports("2026-10-02"), "TEMP");
            check(tempReport.getSampleCount() == 2, "确认后温度日报应补入为2条，实际=" + tempReport.getSampleCount());

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewTaskTemp + "/resolve", confirm));
            assertSuccess(again, "同结论重复复核");
            check(findReport(dailyReports("2026-10-02"), "TEMP").getSampleCount() == 2,
                    "重复复核不应重复计入日报");
        });

        checkpoint("CP9", "日报审核锁定区间，重复审核幂等", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("reviewedByName", "主管老李");
            body.put("requestId", "dr-001");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/daily-reports/" + reportSalinity1001 + "/review", body));
            assertSuccess(resp, "日报审核");
            check(SensorDailyReport.STATUS_REVIEWED.equals(resp.path("data").path("status").asText()),
                    "审核后应为REVIEWED");
            long reviewEvidence = evidenceCount(SensorChangeEvidence.ACTION_REVIEW,
                    SensorChangeEvidence.ENTITY_DAILY_REPORT, reportSalinity1001);
            check(reviewEvidence == 1, "审核证据应为1条，实际=" + reviewEvidence);

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/daily-reports/" + reportSalinity1001 + "/review", body));
            assertSuccess(again, "重复审核");
            check(evidenceCount(SensorChangeEvidence.ACTION_REVIEW,
                    SensorChangeEvidence.ENTITY_DAILY_REPORT, reportSalinity1001) == 1,
                    "重复审核不应再写证据");
        });

        checkpoint("CP10", "迟到数据只能补充未审核区间：已审核指标拒绝补充，未审核指标正常补入", () -> {
            String raw = payload(
                    reading("2026-10-01T11:00:00", 4, "31.0", "7.5", "26.5"));
            JsonNode resp = upload(DEVICE_1, "up-004", raw, sign(SECRET_1, "up-004", raw));
            assertSuccess(resp, "迟到数据上传");
            JsonNode data = resp.path("data");
            check(data.path("lateRejectedCount").asInt() == 1,
                    "已审核盐度应拒绝1条，实际=" + data.path("lateRejectedCount").asInt());
            check(data.path("mergedCount").asInt() == 2, "未审核溶氧/温度应补入2条");
            JsonNode late = data.path("lateRejected");
            check(late.isArray() && late.size() == 1
                            && "SALINITY".equals(late.get(0).path("metricType").asText()),
                    "迟到拒绝应逐条说明指标：" + late);

            SensorDailyReport salinity = dailyReportMapper.selectById(reportSalinity1001);
            check(salinity.getSampleCount() == 3, "已审核盐度日报应保持3条不变，实际=" + salinity.getSampleCount());
            SensorDailyReport doReport = findReport(dailyReports("2026-10-01"), "DO");
            check(doReport.getSampleCount() == 4, "未审核溶氧日报应补入为4条，实际=" + doReport.getSampleCount());
            Long lateCount = readingMapper.selectCount(new QueryWrapper<SensorReading>()
                    .eq("status", SensorReading.STATUS_LATE_REJECTED));
            check(lateCount != null && lateCount == 1L, "迟到读数应留痕1条，实际=" + lateCount);
        });

        checkpoint("CP11", "签名失败与未知设备的包隔离并保留原文，绝不归并", () -> {
            long readingsBefore = readingMapper.selectCount(new QueryWrapper<>());
            String raw = payload(reading("2026-10-05T08:00:00", 1, "28.5", "6.2", "24.3"));
            JsonNode resp = upload(DEVICE_1, "up-005", raw, "deadbeefdeadbeef");
            assertSuccess(resp, "签名失败包接收");
            check("QUARANTINED".equals(resp.path("data").path("status").asText()),
                    "签名失败应隔离，实际=" + resp.path("data").path("status").asText());

            JsonNode resp2 = upload("WQ-SENSOR-UNKNOWN", "up-006", raw, "whatever");
            assertSuccess(resp2, "未知设备包接收");
            check("QUARANTINED".equals(resp2.path("data").path("status").asText()), "未知设备应隔离");

            List<SensorQuarantine> quarantined = quarantineMapper.selectList(new QueryWrapper<>());
            check(quarantined.size() == 2, "隔离区应有2包，实际=" + quarantined.size());
            SensorQuarantine first = quarantined.get(0);
            check(raw.equals(first.getRawPayload()), "隔离包必须保留原始报文原文");
            check("up-005".equals(first.getRequestId()), "隔离包应保留请求号");
            long readingsAfter = readingMapper.selectCount(new QueryWrapper<>());
            check(readingsAfter == readingsBefore, "隔离包绝不能归并：" + readingsBefore + "->" + readingsAfter);
        });

        checkpoint("CP12", "设备更换建立新旧关联：旧设备退役禁传，新设备接续同池日报", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("newDeviceCode", DEVICE_2);
            body.put("newDeviceName", "1号池水质传感器（换新）");
            body.put("newSecretKey", SECRET_2);
            body.put("reason", "旧设备探头老化返厂");
            body.put("operatorName", "设备员小赵");
            body.put("requestId", "replace-001");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/devices/" + deviceId1 + "/replace", body));
            assertSuccess(resp, "设备更换");
            deviceId2 = resp.path("data").path("newDevice").path("id").asLong();
            check(SensorDevice.STATUS_RETIRED.equals(
                    resp.path("data").path("oldDevice").path("status").asText()), "旧设备应退役");

            JsonNode lineage = json(call(HttpMethod.GET,
                    "/api/aqua/sensor/devices/" + deviceId2 + "/lineage", null));
            check(lineage.path("data").size() == 2
                            && DEVICE_1.equals(lineage.path("data").get(0).path("deviceCode").asText())
                            && DEVICE_2.equals(lineage.path("data").get(1).path("deviceCode").asText()),
                    "谱系应为旧→新两台：" + lineage.path("data"));

            String rawOld = payload(reading("2026-10-06T08:00:00", 1, "28.0", null, null));
            JsonNode rejected = upload(DEVICE_1, "up-007", rawOld, sign(SECRET_1, "up-007", rawOld));
            check(!rejected.path("success").asBoolean(true), "退役设备上传应被拒绝");
            check(rejected.path("message").asText().contains("退役"),
                    "拒绝原因应说明设备退役：" + rejected.path("message").asText());

            String raw = payload(reading("2026-10-03T08:00:00", 1, "27.5", null, null));
            JsonNode ok = upload(DEVICE_2, "up-008", raw, sign(SECRET_2, "up-008", raw));
            assertSuccess(ok, "新设备上传");
            SensorDailyReport report1003 = findReport(dailyReports("2026-10-03"), "SALINITY");
            check(report1003 != null && report1003.getPondId().equals(pondId)
                            && report1003.getSampleCount() == 1,
                    "新设备读数应归入同池日报");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/devices/" + deviceId1 + "/replace", body));
            assertSuccess(again, "重复更换请求");
            Long deviceCount = deviceMapper.selectCount(
                    new QueryWrapper<SensorDevice>().eq("device_code", DEVICE_2));
            check(deviceCount != null && deviceCount == 1L, "重复更换不应再建设备，实际=" + deviceCount);
        });

        checkpoint("CP13", "规则调整只生成新版本：旧归并结论冻结不回改，新上传按新版本判定", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                    ruleBody("SEN-RULE-V2", "26.0", "28.0", "5.0", "9.0", "18.0", "30.0")));
            assertSuccess(resp, "发布规则版本V2");
            Long versionV2 = resp.path("data").path("id").asLong();
            SensorRuleVersion v1 = ruleVersionMapper.selectById(versionV1);
            check(SensorRuleVersion.STATUS_SUPERSEDED.equals(v1.getStatus()), "V1应被停用");

            List<SensorReading> oldReadings = readingMapper.selectList(new QueryWrapper<SensorReading>()
                    .eq("metric_type", "SALINITY").eq("status", SensorReading.STATUS_NORMAL)
                    .eq("rule_version_id", versionV1));
            check(!oldReadings.isEmpty(), "V1下归并的正常读数应保持原结论不回改");
            SensorDailyReport oldReport = dailyReportMapper.selectById(reportSalinity1001);
            check(oldReport.getSampleCount() == 3
                            && SensorDailyReport.STATUS_REVIEWED.equals(oldReport.getStatus())
                            && oldReport.getRuleVersionId().equals(versionV1),
                    "旧日报结论应冻结不变");

            String raw = payload(reading("2026-10-04T08:00:00", 1, "28.5", null, null));
            JsonNode uploaded = upload(DEVICE_2, "up-009", raw, sign(SECRET_2, "up-009", raw));
            assertSuccess(uploaded, "V2上传");
            check(uploaded.path("data").path("abnormalCount").asInt() == 1,
                    "28.5在V2（上限28.0）下应判异常");
            List<SensorReading> newReading = readingMapper.selectList(new QueryWrapper<SensorReading>()
                    .eq("device_id", deviceId2).eq("metric_type", "SALINITY")
                    .eq("status", SensorReading.STATUS_ABNORMAL_PENDING));
            check(newReading.size() == 1 && newReading.get(0).getRuleVersionId().equals(versionV2),
                    "新读数应冻结V2版本判定");
        });

        checkpoint("CP14", "全程留证：归并/审核/隔离/更换/版本均可追溯", () -> {
            List<SensorChangeEvidence> mergeEvidence = evidenceMapper.selectList(
                    new QueryWrapper<SensorChangeEvidence>()
                            .eq("entity_type", SensorChangeEvidence.ENTITY_BATCH)
                            .eq("action", SensorChangeEvidence.ACTION_MERGE)
                            .eq("request_id", "up-001"));
            check(mergeEvidence.size() == 1, "up-001归并证据应为1条，实际=" + mergeEvidence.size());

            List<SensorChangeEvidence> quarantineEvidence = evidenceMapper.selectList(
                    new QueryWrapper<SensorChangeEvidence>()
                            .eq("entity_type", SensorChangeEvidence.ENTITY_QUARANTINE)
                            .eq("action", SensorChangeEvidence.ACTION_QUARANTINE));
            check(quarantineEvidence.size() == 2, "隔离证据应为2条，实际=" + quarantineEvidence.size());

            List<SensorChangeEvidence> replaceEvidence = evidenceMapper.selectList(
                    new QueryWrapper<SensorChangeEvidence>()
                            .eq("entity_type", SensorChangeEvidence.ENTITY_SUCCESSION)
                            .eq("action", SensorChangeEvidence.ACTION_REPLACE)
                            .eq("request_id", "replace-001"));
            check(replaceEvidence.size() == 1, "新旧关联证据应为1条，实际=" + replaceEvidence.size());

            List<SensorChangeEvidence> supersedeEvidence = evidenceMapper.selectList(
                    new QueryWrapper<SensorChangeEvidence>()
                            .eq("entity_type", SensorChangeEvidence.ENTITY_RULE_VERSION)
                            .eq("action", SensorChangeEvidence.ACTION_SUPERSEDE));
            check(supersedeEvidence.size() == 1, "版本停用证据应为1条，实际=" + supersedeEvidence.size());

            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/sensor/evidence?requestId=up-004", null));
            assertSuccess(resp, "按requestId查证据");
            check(resp.path("data").size() >= 2, "up-004应留有归并与迟到拒绝证据");
        });

        report.add("验收结论：全部通过");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Map<String, Object> ruleBody(String versionNo, String salMin, String salMax,
                                         String doMin, String doMax, String tempMin, String tempMax) {
        Map<String, Object> body = new HashMap<>();
        body.put("versionNo", versionNo);
        body.put("salinityMin", salMin);
        body.put("salinityMax", salMax);
        body.put("doMin", doMin);
        body.put("doMax", doMax);
        body.put("tempMin", tempMin);
        body.put("tempMax", tempMax);
        return body;
    }

    private Map<String, Object> deviceBody(String code, String name, String secret, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceCode", code);
        body.put("deviceName", name);
        body.put("pondId", pondId);
        body.put("secretKey", secret);
        body.put("installedDate", "2026-09-20");
        body.put("requestId", requestId);
        return body;
    }

    /** 手工拼接报文原文：签名必须与服务端收到的字节完全一致 */
    private String reading(String deviceTime, int seqNo, String salinity, String dissolvedOxygen, String temperature) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"deviceTime\":\"").append(deviceTime).append("\",\"seqNo\":").append(seqNo);
        if (salinity != null) { sb.append(",\"salinity\":").append(salinity); }
        if (dissolvedOxygen != null) { sb.append(",\"dissolvedOxygen\":").append(dissolvedOxygen); }
        if (temperature != null) { sb.append(",\"temperature\":").append(temperature); }
        sb.append("}");
        return sb.toString();
    }

    private String payload(String... readings) {
        return "{\"readings\":[" + String.join(",", readings) + "]}";
    }

    private String sign(String secret, String requestId, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((requestId + "\n" + rawBody).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private JsonNode upload(String deviceCode, String requestId, String rawBody, String signature) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        headers.add("X-Device-Code", deviceCode);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Signature", signature);
        ResponseEntity<String> resp = rest.exchange("/api/aqua/sensor/uploads", HttpMethod.POST,
                new HttpEntity<>(rawBody, headers), String.class);
        return json(resp);
    }

    private List<SensorDailyReport> dailyReports(String reportDate) {
        return dailyReportMapper.selectList(new QueryWrapper<SensorDailyReport>()
                .eq("pond_id", pondId).eq("report_date", reportDate).orderByAsc("metric_type"));
    }

    private SensorDailyReport findReport(List<SensorDailyReport> reports, String metricType) {
        for (SensorDailyReport report : reports) {
            if (metricType.equals(report.getMetricType())) {
                return report;
            }
        }
        return null;
    }

    private long evidenceCount(String action, String entityType, Long entityId) {
        Long count = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                .eq("action", action).eq("entity_type", entityType).eq("entity_id", entityId));
        return count == null ? 0L : count;
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
        sb.append("\n========== 池塘水质传感器接入验收报告 ==========\n");
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
        HttpEntity<Object> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        return rest.exchange(url, method, entity, String.class);
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
