package com.evops.aqua.sensor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.entity.SensorQuarantine;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewTask;
import com.evops.aqua.sensor.mapper.SensorChangeEvidenceMapper;
import com.evops.aqua.sensor.mapper.SensorDailyReportMapper;
import com.evops.aqua.sensor.mapper.SensorQuarantineMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewTaskMapper;
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
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 离线归并修复验收：
 * CP1 乱序（序号/时间倒置）上传——归并槽位去重与先后顺序无关，日报仍唯一计数；
 * CP2 跨日边界——23:59 与次日 00:00 分属两天日报，半开区间端点不串日；
 * CP3 设备更换后新设备补传旧槽位——沿池塘谱系去重，溶氧最低值/样本数不被重复放大；
 * CP4 隔离包（签名失败）绝不进入汇总；
 * CP5 高风险观察：置观察后离线补传 403 拒绝、复核确认只留痕不汇总（同步限制）；
 * CP6 解除观察：OPEN 日报按各自原规则版本补算，REVIEWED 日报冻结原版本不回改；
 * CP7 离线归并规则版本先变更，已建日报仍按原版本核对、样本不重复；
 * CP8 拒绝/限制全程只追加凭证，不覆盖已有证据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// 独立内存库：与其他 @SpringBootTest 验收类隔离，避免全局读数计数/规则版本等相互污染
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:evops-risk;DB_CLOSE_DELAY=-1")
class SensorReorderRiskWatchAcceptanceTest {

    private static final String SECRET_1 = "risk-secret-key-01";
    private static final String SECRET_2 = "risk-secret-key-02";
    private static final String DEVICE_1 = "WQ-RISK-01";
    private static final String DEVICE_2 = "WQ-RISK-02";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
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

    @Test
    void acceptanceReorderAndRiskWatch() {
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

        checkpoint("CP1", "乱序上传：序号/时间倒置不影响槽位去重，日报唯一计数", () -> {
            setupPondAndRuleV1();
            deviceId1 = registerDevice(DEVICE_1, SECRET_1, "risk-dev-001");

            // 先传 seq=3/10:00，再传 seq=1/08:00、seq=2/09:00（乱序）
            String first = payload(reading("2026-11-01T10:00:00", 3, "30.0", "7.0", "26.0"));
            JsonNode r1 = upload(DEVICE_1, "risk-up-001", first, sign(SECRET_1, "risk-up-001", first));
            assertSuccess(r1, "乱序首包");
            check(r1.path("data").path("mergedCount").asInt() == 3, "首包应归并3条指标");

            String second = payload(
                    reading("2026-11-01T08:00:00", 1, "28.5", "5.2", "24.0"),
                    reading("2026-11-01T09:00:00", 2, "29.0", "6.0", "25.0"));
            JsonNode r2 = upload(DEVICE_1, "risk-up-002", second, sign(SECRET_1, "risk-up-002", second));
            assertSuccess(r2, "乱序次包");
            check(r2.path("data").path("mergedCount").asInt() == 6, "次包6条均为新槽位，实际="
                    + r2.path("data").path("mergedCount").asInt());

            // 再次乱序重放部分槽位（新 requestId）：seq 顺序颠倒且与已有重叠
            String overlap = payload(
                    reading("2026-11-01T09:00:00", 2, "29.0", "6.0", "25.0"),
                    reading("2026-11-01T08:00:00", 1, "28.5", "5.2", "24.0"));
            JsonNode r3 = upload(DEVICE_1, "risk-up-003", overlap, sign(SECRET_1, "risk-up-003", overlap));
            assertSuccess(r3, "乱序重叠包");
            check(r3.path("data").path("duplicateCount").asInt() == 6, "重叠6条应全部判重，实际="
                    + r3.path("data").path("duplicateCount").asInt());
            check(r3.path("data").path("mergedCount").asInt() == 0, "重叠包不应新增归并");

            SensorDailyReport doReport = report("2026-11-01", "DO");
            check(doReport.getSampleCount() == 3, "乱序+重叠后溶氧样本应为3，实际=" + doReport.getSampleCount());
            check(doReport.getMinValue().compareTo(new BigDecimal("5.2")) == 0,
                    "溶氧最低值应为5.2（未被重复放大），实际=" + doReport.getMinValue());
            check(doReport.getMaxValue().compareTo(new BigDecimal("7.0")) == 0,
                    "溶氧最高值应为7.0，实际=" + doReport.getMaxValue());
            long readingRows = readingMapper.selectCount(new QueryWrapper<SensorReading>()
                    .eq("pond_id", pondId).eq("metric_type", "DO"));
            check(readingRows == 3L, "溶氧读数行应只有3，实际=" + readingRows);
        });

        checkpoint("CP2", "跨日边界：23:59 与次日 00:00 分属两天，端点不串日", () -> {
            String raw = payload(
                    reading("2026-11-01T23:59:00", 10, "31.0", "8.8", "27.0"),
                    reading("2026-11-02T00:00:00", 11, "27.0", "5.5", "20.0"));
            JsonNode resp = upload(DEVICE_1, "risk-up-004", raw, sign(SECRET_1, "risk-up-004", raw));
            assertSuccess(resp, "跨日边界包");
            check(resp.path("data").path("mergedCount").asInt() == 6, "跨日6条均应归并");

            SensorDailyReport day1Do = report("2026-11-01", "DO");
            SensorDailyReport day2Do = report("2026-11-02", "DO");
            check(day1Do.getSampleCount() == 4, "11-01溶氧样本应为4（含23:59），实际=" + day1Do.getSampleCount());
            check(day1Do.getMaxValue().compareTo(new BigDecimal("8.8")) == 0,
                    "11-01溶氧最高应含23:59的8.8，实际=" + day1Do.getMaxValue());
            check(day2Do.getSampleCount() == 1, "11-02溶氧样本应为1（仅00:00），实际=" + day2Do.getSampleCount());
            check(day2Do.getMinValue().compareTo(new BigDecimal("5.5")) == 0,
                    "11-02溶氧应为00:00的5.5，实际=" + day2Do.getMinValue());
        });

        checkpoint("CP3", "设备更换后新设备补传旧槽位：沿池塘谱系去重，日报不重复", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("newDeviceCode", DEVICE_2);
            body.put("newDeviceName", "高风险池换新传感器");
            body.put("newSecretKey", SECRET_2);
            body.put("reason", "重复补传排查后换新设备");
            body.put("operatorName", "设备员小赵");
            body.put("requestId", "risk-replace-001");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/devices/" + deviceId1 + "/replace", body));
            assertSuccess(resp, "设备更换");
            deviceId2 = resp.path("data").path("newDevice").path("id").asLong();

            SensorDailyReport before = report("2026-11-01", "DO");
            int samplesBefore = before.getSampleCount();
            BigDecimal minBefore = before.getMinValue();

            // 新设备补传旧设备已归并的同（时间, 序号）槽位——设备 id 不同但池塘谱系相同
            String retransmit = payload(
                    reading("2026-11-01T08:00:00", 1, "28.5", "5.2", "24.0"),
                    reading("2026-11-01T09:00:00", 2, "29.0", "6.0", "25.0"),
                    reading("2026-11-01T10:00:00", 3, "30.0", "7.0", "26.0"),
                    reading("2026-11-01T23:59:00", 10, "31.0", "8.8", "27.0"));
            JsonNode dup = upload(DEVICE_2, "risk-up-005", retransmit,
                    sign(SECRET_2, "risk-up-005", retransmit));
            assertSuccess(dup, "新设备补传旧槽位");
            check(dup.path("data").path("duplicateCount").asInt() == 12,
                    "新设备补传的12条指标应全部沿谱系判重，实际="
                            + dup.path("data").path("duplicateCount").asInt());
            check(dup.path("data").path("mergedCount").asInt() == 0, "补传不应新增归并读数");

            SensorDailyReport after = report("2026-11-01", "DO");
            check(after.getSampleCount() == samplesBefore,
                    "换设备补传后溶氧样本数不得放大：" + samplesBefore + "->" + after.getSampleCount());
            check(after.getMinValue().compareTo(minBefore) == 0,
                    "换设备补传后溶氧最低值不得变化：" + minBefore + "->" + after.getMinValue());

            // 新设备补传一个真正缺失的槽位：应正常归并进同池日报
            String fresh = payload(reading("2026-11-01T12:00:00", 20, "29.5", "6.8", "25.5"));
            JsonNode added = upload(DEVICE_2, "risk-up-006", fresh,
                    sign(SECRET_2, "risk-up-006", fresh));
            assertSuccess(added, "新设备补传新槽位");
            check(added.path("data").path("mergedCount").asInt() == 3, "新槽位3指标应归并");
            SensorDailyReport grown = report("2026-11-01", "DO");
            check(grown.getSampleCount() == samplesBefore + 1,
                    "仅新槽位应使溶氧样本+1，实际=" + grown.getSampleCount());
        });

        checkpoint("CP4", "隔离包绝不进入汇总：新设备签名失败包留痕不影响日报", () -> {
            SensorDailyReport before = report("2026-11-02", "DO");
            int samplesBefore = before == null ? -1 : before.getSampleCount();
            long quarantineBefore = quarantineMapper.selectCount(new QueryWrapper<>());

            String raw = payload(
                    reading("2026-11-02T08:00:00", 1, "28.5", "1.1", "24.3"),
                    reading("2026-11-02T09:00:00", 2, "28.6", "1.2", "24.4"));
            JsonNode resp = upload(DEVICE_2, "risk-up-007", raw, "0bad0bad0bad");
            assertSuccess(resp, "签名失败包接收");
            check("QUARANTINED".equals(resp.path("data").path("status").asText()),
                    "签名失败应整包隔离，实际=" + resp.path("data").path("status").asText());
            long quarantineAfter = quarantineMapper.selectCount(new QueryWrapper<>());
            check(quarantineAfter == quarantineBefore + 1, "隔离区应新增1包");
            List<SensorQuarantine> all = quarantineMapper.selectList(new QueryWrapper<>());
            check(raw.equals(all.get(all.size() - 1).getRawPayload()), "隔离包必须保留原文");

            // 隔离包内读数绝不落 t_sensor_reading，日报汇总不变
            long leaked = readingMapper.selectCount(new QueryWrapper<SensorReading>()
                    .eq("pond_id", pondId).eq("device_time", "2026-11-02T08:00:00"));
            check(leaked == 0L, "隔离包读数不得落入归并表，实际=" + leaked);
            SensorDailyReport after = report("2026-11-02", "DO");
            int samplesAfter = after == null ? -1 : after.getSampleCount();
            check(samplesAfter == samplesBefore, "隔离包不得改变日报汇总："
                    + samplesBefore + "->" + samplesAfter);
        });

        checkpoint("CP5", "高风险观察：补传归并403拒绝，复核确认只留痕、日报汇总同步限制", () -> {
            // 先在正常状态下传一个异常读数（溶氧 2.0 低于 V1 下限 5.0），产生待复核任务
            String abnormal = payload(reading("2026-11-03T08:00:00", 1, "28.0", "2.0", "24.0"));
            JsonNode up = upload(DEVICE_2, "risk-up-008", abnormal,
                    sign(SECRET_2, "risk-up-008", abnormal));
            assertSuccess(up, "异常读数上传");
            check(up.path("data").path("abnormalCount").asInt() == 1, "应有1条溶氧异常待复核");
            SensorReviewTask task = reviewTaskMapper.selectOne(new QueryWrapper<SensorReviewTask>()
                    .eq("pond_id", pondId).eq("metric_type", "DO")
                    .eq("status", SensorReviewTask.STATUS_PENDING)
                    .eq("device_time", "2026-11-03T08:00:00").last("limit 1"));
            check(task != null, "应查到11-03溶氧待复核任务");

            long readingsBefore = readingMapper.selectCount(new QueryWrapper<>());
            SensorDailyReport day3Before = report("2026-11-03", "DO");
            check(day3Before.getSampleCount() == 0, "异常读数暂不计入，11-03溶氧样本应为0，实际="
                    + day3Before.getSampleCount());

            // 置为高风险观察
            JsonNode watchOn = postRiskWatch("/watch-on", "risk-watch-on-001",
                    "发现传感器补传造成日报重复，挂高风险观察");
            assertSuccess(watchOn, "置高风险观察");
            check(Boolean.TRUE.equals(watchOn.path("data").path("watchEnabled").asBoolean()),
                    "开关应为观察中");

            // 观察期间补传：403，读数/日报无变化，独立事务留拒绝证据
            String retransmit = payload(reading("2026-11-03T09:00:00", 2, "28.1", "6.1", "24.1"));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.COOKIE, sessionCookie);
            headers.add("X-Device-Code", DEVICE_2);
            headers.add("X-Request-Id", "risk-up-009");
            headers.add("X-Signature", sign(SECRET_2, "risk-up-009", retransmit));
            ResponseEntity<String> denied = rest.exchange("/api/aqua/sensor/uploads", HttpMethod.POST,
                    new HttpEntity<>(retransmit, headers), String.class);
            check(denied.getStatusCodeValue() == 403, "观察期间补传应返回403，实际="
                    + denied.getStatusCodeValue());
            check(json(denied).path("message").asText().contains("日报重复"),
                    "403应说明观察项，实际=" + json(denied).path("message").asText());
            long readingsAfter = readingMapper.selectCount(new QueryWrapper<>());
            check(readingsAfter == readingsBefore, "拒绝归并不得新增读数："
                    + readingsBefore + "->" + readingsAfter);
            SensorDailyReport day3After = report("2026-11-03", "DO");
            check(day3After.getSampleCount() == 0, "观察期间日报不得汇总新增，样本应仍为0，实际="
                    + day3After.getSampleCount());

            long deniedEvidence = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                    .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                    .eq("action", SensorChangeEvidence.ACTION_DENIED)
                    .eq("request_id", "risk-up-009"));
            check(deniedEvidence == 1L, "拒绝归并应独立事务留证1条，实际=" + deniedEvidence);

            // 观察期间复核确认：结论落库并留痕，但日报汇总同步限制不重算
            Map<String, Object> confirm = new HashMap<>();
            confirm.put("decision", "CONFIRMED");
            confirm.put("reviewedByName", "质检员小王");
            confirm.put("reviewRemark", "高风险观察期间先确认，日报待解除后补算");
            confirm.put("requestId", "risk-rv-001");
            JsonNode rv = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + task.getId() + "/resolve", confirm));
            assertSuccess(rv, "观察期间复核确认");
            SensorReading confirmedReading = readingMapper.selectById(task.getReadingId());
            check(SensorReading.STATUS_CONFIRMED.equals(confirmedReading.getStatus()),
                    "复核确认应把读数置CONFIRMED");
            SensorDailyReport day3Still = report("2026-11-03", "DO");
            check(day3Still.getSampleCount() == 0,
                    "观察期间日报汇总同步限制：CONFIRMED 暂不计入，实际=" + day3Still.getSampleCount());
        });

        checkpoint("CP6", "解除观察：OPEN日报按原版本补算，REVIEWED日报冻结不回改", () -> {
            // 先审核 11-02 溶氧日报（观察期间允许审核），审核后即便解除观察也不得回改
            SensorDailyReport day2 = report("2026-11-02", "DO");
            Map<String, Object> review = new HashMap<>();
            review.put("reviewedByName", "主管老李");
            review.put("requestId", "risk-dr-001");
            JsonNode rv = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/daily-reports/" + day2.getId() + "/review", review));
            assertSuccess(rv, "观察期间审核11-02溶氧日报");
            int reviewedSamples = day2.getSampleCount();
            Long reviewedVersion = day2.getRuleVersionId();

            // 解除观察：OPEN 日报统一补算
            JsonNode off = postRiskWatch("/watch-off", "risk-watch-off-001",
                    "重复计数缺陷已修复并验收，解除观察");
            assertSuccess(off, "解除观察");
            check(Boolean.FALSE.equals(off.path("data").path("watch").path("watchEnabled").asBoolean()),
                    "开关应为未观察");
            check(off.path("data").path("openReportRecalcCount").asInt() >= 1,
                    "解除观察应至少补算1张OPEN日报，实际="
                            + off.path("data").path("openReportRecalcCount").asInt());

            // 观察期间 CONFIRMED 的 11-03 溶氧补入 OPEN 日报
            SensorDailyReport day3 = report("2026-11-03", "DO");
            check(day3.getSampleCount() == 1,
                    "解除观察后CONFIRMED读数应补入为1条，实际=" + day3.getSampleCount());
            check(day3.getMinValue().compareTo(new BigDecimal("2.0")) == 0,
                    "补入后溶氧值应为2.0，实际=" + day3.getMinValue());

            // REVIEWED 日报冻结：样本数与规则版本不变
            SensorDailyReport day2After = dailyReportMapper.selectById(day2.getId());
            check(SensorDailyReport.STATUS_REVIEWED.equals(day2After.getStatus())
                            && day2After.getSampleCount() == reviewedSamples
                            && day2After.getRuleVersionId().equals(reviewedVersion),
                    "已审核日报解除观察后仍须冻结：样本=" + day2After.getSampleCount()
                            + "，版本=" + day2After.getRuleVersionId());

            // 解除后补传恢复受理
            String raw = payload(reading("2026-11-03T10:00:00", 3, "28.2", "6.3", "24.2"));
            JsonNode up = upload(DEVICE_2, "risk-up-010", raw, sign(SECRET_2, "risk-up-010", raw));
            assertSuccess(up, "解除观察后补传恢复");
            check(up.path("data").path("mergedCount").asInt() == 3, "恢复后应正常归并3条");
        });

        checkpoint("CP7", "离线归并规则版本先变更：已建日报仍按原版本核对、不重复计数", () -> {
            // 发布 V2（溶氧下限提到 6.0）：只影响之后新建日报，已建 OPEN 日报重算仍按 V1 口径且版本不变
            JsonNode v2 = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                    ruleBody("RISK-RULE-V2", "25.0", "32.0", "6.0", "9.0", "18.0", "30.0")));
            assertSuccess(v2, "发布规则版本V2");
            Long versionV2 = v2.path("data").path("id").asLong();

            SensorDailyReport day1 = report("2026-11-01", "DO");
            Long day1VersionBefore = day1.getRuleVersionId();
            int day1SamplesBefore = day1.getSampleCount();
            check(day1VersionBefore.equals(versionV1), "11-01日报应仍冻结V1");

            // V2 生效后用 V2 期间的版本入参触发 11-01 重算（补传新槽位），日报版本必须保持 V1
            String raw = payload(reading("2026-11-01T13:00:00", 21, "29.6", "6.4", "25.6"));
            JsonNode up = upload(DEVICE_2, "risk-up-011", raw, sign(SECRET_2, "risk-up-011", raw));
            assertSuccess(up, "V2后补传11-01新槽位");

            SensorDailyReport day1After = report("2026-11-01", "DO");
            check(day1After.getRuleVersionId().equals(versionV1),
                    "离线归并先换版，已建日报必须仍按原版本V1核对，实际版本id="
                            + day1After.getRuleVersionId() + "（V1=" + versionV1 + "，V2=" + versionV2 + "）");
            check(day1After.getSampleCount() == day1SamplesBefore + 1,
                    "日报仅因新槽位+1且不重复，实际=" + day1After.getSampleCount());

            // V2 下新建的日报才冻结 V2：溶氧 5.9 在 V2 判异常，在 V1 本应正常
            String edge = payload(reading("2026-11-04T08:00:00", 1, "28.0", "5.9", "24.0"));
            JsonNode edgeUp = upload(DEVICE_2, "risk-up-012", edge, sign(SECRET_2, "risk-up-012", edge));
            assertSuccess(edgeUp, "V2边界读数上传");
            check(edgeUp.path("data").path("abnormalCount").asInt() == 1,
                    "溶氧5.9低于V2下限6.0应判异常，实际="
                            + edgeUp.path("data").path("abnormalCount").asInt());
        });

        checkpoint("CP8", "凭证只追加：观察开关/拒绝/审核各有独立证据行，无覆盖", () -> {
            long onEvidence = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                    .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                    .eq("action", SensorChangeEvidence.ACTION_WATCH_ON));
            long offEvidence = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                    .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                    .eq("action", SensorChangeEvidence.ACTION_WATCH_OFF));
            check(onEvidence == 1L && offEvidence == 1L,
                    "观察开启/解除证据应各1条，实际 on=" + onEvidence + " off=" + offEvidence);
            long deniedRows = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                    .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                    .eq("action", SensorChangeEvidence.ACTION_DENIED));
            check(deniedRows >= 1L, "拒绝证据应只追加存在，实际=" + deniedRows);

            // 再次置观察/解除，证据只追加不覆盖
            postRiskWatch("/watch-on", "risk-watch-on-002", "回归复验再次观察");
            postRiskWatch("/watch-off", "risk-watch-off-002", "回归复验解除");
            long onEvidence2 = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                    .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                    .eq("action", SensorChangeEvidence.ACTION_WATCH_ON));
            long offEvidence2 = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                    .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                    .eq("action", SensorChangeEvidence.ACTION_WATCH_OFF));
            check(onEvidence2 == 2L && offEvidence2 == 2L,
                    "重复切换应只追加为2条/动作，实际 on=" + onEvidence2 + " off=" + offEvidence2);
        });

        report.add("验收结论：全部通过");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private void setupPondAndRuleV1() {
        Map<String, Object> pond = new HashMap<>();
        pond.put("pondCode", "SEN-RISK-P01");
        pond.put("pondName", "高风险观察池");
        pond.put("seaArea", "东山湾海区");
        pond.put("waterDepthM", "1.80");
        pond.put("salinityPpt", "28.5");
        JsonNode pondResp = json(call(HttpMethod.POST, "/api/aqua/ponds", pond));
        assertSuccess(pondResp, "建池");
        pondId = pondResp.path("data").path("id").asLong();

        JsonNode rule = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                ruleBody("RISK-RULE-V1", "25.0", "32.0", "5.0", "9.0", "18.0", "30.0")));
        assertSuccess(rule, "发布规则版本V1");
        versionV1 = rule.path("data").path("id").asLong();
    }

    private Long registerDevice(String code, String secret, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceCode", code);
        body.put("deviceName", code + "-传感器");
        body.put("pondId", pondId);
        body.put("secretKey", secret);
        body.put("installedDate", "2026-09-20");
        body.put("requestId", requestId);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/devices", body));
        assertSuccess(resp, "登记设备" + code);
        return resp.path("data").path("id").asLong();
    }

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

    private JsonNode postRiskWatch(String path, String requestId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("requestId", requestId);
        return json(call(HttpMethod.POST, "/api/aqua/sensor/risk-watch" + path, body));
    }

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

    private JsonNode upload(String deviceCode, String requestId, String rawBody, String signature) {
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

    private SensorDailyReport report(String reportDate, String metricType) {
        List<SensorDailyReport> list = dailyReportMapper.selectList(new QueryWrapper<SensorDailyReport>()
                .eq("pond_id", pondId).eq("report_date", reportDate).eq("metric_type", metricType));
        return list.isEmpty() ? null : list.get(0);
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
        sb.append("\n========== 离线归并修复与高风险观察验收报告 ==========\n");
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

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            throw new IllegalStateException("响应不是JSON：" + ex.getMessage() + "，原文=" + response.getBody());
        }
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
