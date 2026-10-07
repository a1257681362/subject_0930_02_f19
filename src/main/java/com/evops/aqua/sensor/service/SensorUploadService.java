package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.UploadPayload;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.entity.SensorQuarantine;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewTask;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.entity.SensorUploadBatch;
import com.evops.aqua.sensor.mapper.SensorQuarantineMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewTaskMapper;
import com.evops.aqua.sensor.mapper.SensorUploadBatchMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备离线批量上传归并（核心）：
 * 1) 验签——HMAC-SHA256(设备密钥, requestId + "\n" + 原始报文)，失败/未知设备整包隔离并保留原文；
 * 2) 归并——按（设备, 指标, 设备时间, 序号）自然键去重，重新上传以首次为准；
 * 3) 判定——按归并时生效规则版本判异常，异常读数进待复核队列暂不计入日报；
 * 4) 迟到——落入已审核日报区间的读数记 LATE_REJECTED 留痕，不补充进报；
 * 5) 日报——由归并读数重算得出，重复上传不重复计入。
 * 整包按 requestId 幂等：重复上传返回首次归并结果。
 */
@Service
public class SensorUploadService {

    private final SensorUploadBatchMapper batchMapper;
    private final SensorReadingMapper readingMapper;
    private final SensorReviewTaskMapper reviewTaskMapper;
    private final SensorQuarantineMapper quarantineMapper;
    private final SensorDeviceService deviceService;
    private final SensorRuleVersionService ruleVersionService;
    private final SensorDailyReportService dailyReportService;
    private final SensorEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public SensorUploadService(SensorUploadBatchMapper batchMapper,
                               SensorReadingMapper readingMapper,
                               SensorReviewTaskMapper reviewTaskMapper,
                               SensorQuarantineMapper quarantineMapper,
                               SensorDeviceService deviceService,
                               SensorRuleVersionService ruleVersionService,
                               SensorDailyReportService dailyReportService,
                               SensorEvidenceService evidenceService,
                               ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.readingMapper = readingMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.quarantineMapper = quarantineMapper;
        this.deviceService = deviceService;
        this.ruleVersionService = ruleVersionService;
        this.dailyReportService = dailyReportService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 接收设备批量上传包。rawBody 为原始报文串（验签与隔离留原文都基于它）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upload(String deviceCode, String requestId, String signature, String rawBody) {
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少设备编码请求头");
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少请求号请求头（用于整包幂等）");
        }
        // 整包幂等：同一 requestId 重复上传返回首次归并结果，不重复归并
        SensorUploadBatch existed = findBatch(requestId);
        if (existed != null) {
            return mergeResult(existed, true);
        }

        SensorDevice device = deviceService.findByCode(deviceCode);
        if (device == null) {
            SensorQuarantine q = quarantine(deviceCode, requestId, signature, rawBody, "设备未登记：" + deviceCode);
            return quarantineResult(q);
        }
        if (!SensorDevice.STATUS_ACTIVE.equals(device.getStatus())) {
            throw new IllegalArgumentException("设备[" + deviceCode + "]已退役，禁止上传数据");
        }
        if (!signatureOk(device.getSecretKey(), requestId, rawBody, signature)) {
            SensorQuarantine q = quarantine(deviceCode, requestId, signature, rawBody,
                    "签名校验失败，整包隔离");
            return quarantineResult(q);
        }

        UploadPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, UploadPayload.class);
        } catch (Exception ex) {
            SensorQuarantine q = quarantine(deviceCode, requestId, signature, rawBody,
                    "报文解析失败：" + ex.getMessage());
            return quarantineResult(q);
        }
        if (payload.getReadings() == null || payload.getReadings().isEmpty()) {
            throw new IllegalArgumentException("上传报文不含任何采样记录");
        }

        SensorRuleVersion ruleVersion = ruleVersionService.requireActive();
        SensorUploadBatch batch = new SensorUploadBatch();
        batch.setRequestId(requestId);
        batch.setDeviceId(device.getId());
        batch.setDeviceCode(deviceCode);
        batch.setSignature(signature);
        batch.setSignatureOk(Boolean.TRUE);
        batch.setStatus(SensorUploadBatch.STATUS_MERGED);
        batch.setRuleVersionId(ruleVersion.getId());
        batch.setRawPayload(rawBody);
        batch.setReceivedTime(LocalDateTime.now());
        batch.setTotalCount(0);
        batch.setMergedCount(0);
        batch.setDuplicateCount(0);
        batch.setLateRejectedCount(0);
        batch.setAbnormalCount(0);
        batchMapper.insert(batch);

        int total = 0;
        // 分类计数：[0]新归并 [1]重复 [2]迟到拒绝 [3]异常待复核（局部变量，并发安全）
        int[] counts = new int[4];
        List<Map<String, Object>> lateRejectedItems = new ArrayList<>();
        Set<String> affectedDays = new LinkedHashSet<>();
        for (UploadPayload.UploadReadingItem item : payload.getReadings()) {
            if (item.getDeviceTime() == null || item.getSeqNo() == null) {
                throw new IllegalArgumentException("采样记录缺少设备时间或序号，整包拒绝");
            }
            total += mergeMetric(batch, device, item, SensorReading.METRIC_SALINITY,
                    item.getSalinity(), ruleVersion, counts, lateRejectedItems, affectedDays);
            total += mergeMetric(batch, device, item, SensorReading.METRIC_DO,
                    item.getDissolvedOxygen(), ruleVersion, counts, lateRejectedItems, affectedDays);
            total += mergeMetric(batch, device, item, SensorReading.METRIC_TEMP,
                    item.getTemperature(), ruleVersion, counts, lateRejectedItems, affectedDays);
        }
        int merged = counts[0], duplicate = counts[1], lateRejected = counts[2], abnormal = counts[3];

        for (String affected : affectedDays) {
            String[] parts = affected.split("\\|");
            dailyReportService.recalc(Long.valueOf(parts[0]), LocalDate.parse(parts[1]), parts[2],
                    ruleVersion.getId(), requestId);
        }

        batch.setTotalCount(total);
        batch.setMergedCount(merged);
        batch.setDuplicateCount(duplicate);
        batch.setLateRejectedCount(lateRejected);
        batch.setAbnormalCount(abnormal);
        batchMapper.updateById(batch);
        evidenceService.record(SensorChangeEvidence.ENTITY_BATCH, batch.getId(),
                SensorChangeEvidence.ACTION_MERGE, requestId, null, batch,
                "设备[" + deviceCode + "]批量上传归并：总" + total + "，新归并" + merged
                        + "，重复" + duplicate + "，迟到拒绝" + lateRejected + "，异常待复核" + abnormal);

        Map<String, Object> result = mergeResult(batch, false);
        result.put("lateRejected", lateRejectedItems);
        return result;
    }

    public SensorUploadBatch findBatch(String requestId) {
        List<SensorUploadBatch> list = batchMapper.selectList(
                new QueryWrapper<SensorUploadBatch>().eq("request_id", requestId));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SensorReading> listReadings(Long pondId, String metricType, String status) {
        QueryWrapper<SensorReading> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(metricType != null && !metricType.trim().isEmpty(), "metric_type", metricType);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("device_time", "seq_no");
        return readingMapper.selectList(wrapper);
    }

    public List<SensorQuarantine> listQuarantine() {
        return quarantineMapper.selectList(new QueryWrapper<SensorQuarantine>().orderByAsc("id"));
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    /**
     * 归并单条指标读数。返回 1 表示该指标参与了归并判定（计入 total），0 表示该指标缺项。
     * counts 依次累计：[0]新归并 [1]重复 [2]迟到拒绝 [3]异常待复核。
     */
    private int mergeMetric(SensorUploadBatch batch, SensorDevice device,
                            UploadPayload.UploadReadingItem item, String metricType,
                            BigDecimal value, SensorRuleVersion ruleVersion, int[] counts,
                            List<Map<String, Object>> lateRejectedItems, Set<String> affectedDays) {
        if (value == null) {
            return 0;
        }
        Long exists = readingMapper.selectCount(new QueryWrapper<SensorReading>()
                .eq("device_id", device.getId())
                .eq("metric_type", metricType)
                .eq("device_time", item.getDeviceTime())
                .eq("seq_no", item.getSeqNo()));
        if (exists != null && exists > 0) {
            counts[1]++; // 重复槽位：以首次归并为准，不重复计入
            return 1;
        }
        LocalDate reportDate = item.getDeviceTime().toLocalDate();
        SensorReading reading = new SensorReading();
        reading.setBatchId(batch.getId());
        reading.setDeviceId(device.getId());
        reading.setPondId(device.getPondId());
        reading.setMetricType(metricType);
        reading.setDeviceTime(item.getDeviceTime());
        reading.setSeqNo(item.getSeqNo());
        reading.setReadingValue(value);
        reading.setRuleVersionId(ruleVersion.getId());

        if (dailyReportService.isReviewed(device.getPondId(), reportDate, metricType)) {
            // 迟到数据：已审核区间只能留痕，不能补充进报
            reading.setStatus(SensorReading.STATUS_LATE_REJECTED);
            readingMapper.insert(reading);
            counts[2]++;
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("metricType", metricType);
            rejected.put("deviceTime", item.getDeviceTime().toString());
            rejected.put("seqNo", item.getSeqNo());
            rejected.put("reason", reportDate + "「" + metricType + "」日报已审核，迟到数据只能补充未审核区间");
            lateRejectedItems.add(rejected);
            evidenceService.record(SensorChangeEvidence.ENTITY_READING, reading.getId(),
                    SensorChangeEvidence.ACTION_LATE_REJECT, batch.getRequestId(), null, reading,
                    "迟到读数落入已审核区间，留痕但不入日报：" + metricType + " " + item.getDeviceTime());
            return 1;
        }

        if (inRange(ruleVersion, metricType, value)) {
            reading.setStatus(SensorReading.STATUS_NORMAL);
            readingMapper.insert(reading);
            counts[0]++;
        } else {
            // 异常读数：进待复核队列，暂不计入日报
            reading.setStatus(SensorReading.STATUS_ABNORMAL_PENDING);
            readingMapper.insert(reading);
            counts[0]++;
            counts[3]++;
            SensorReviewTask task = new SensorReviewTask();
            task.setReadingId(reading.getId());
            task.setPondId(device.getPondId());
            task.setMetricType(metricType);
            task.setDeviceTime(item.getDeviceTime());
            task.setReadingValue(value);
            task.setStatus(SensorReviewTask.STATUS_PENDING);
            reviewTaskMapper.insert(task);
            evidenceService.record(SensorChangeEvidence.ENTITY_REVIEW_TASK, task.getId(),
                    SensorChangeEvidence.ACTION_CREATE, batch.getRequestId(), null, task,
                    "异常读数进待复核队列：" + metricType + "=" + value + " 超出规则版本["
                            + ruleVersion.getVersionNo() + "]正常范围");
        }
        affectedDays.add(device.getPondId() + "|" + reportDate + "|" + metricType);
        return 1;
    }

    private boolean inRange(SensorRuleVersion rule, String metricType, BigDecimal value) {
        BigDecimal min;
        BigDecimal max;
        switch (metricType) {
            case SensorReading.METRIC_SALINITY:
                min = rule.getSalinityMin(); max = rule.getSalinityMax(); break;
            case SensorReading.METRIC_DO:
                min = rule.getDoMin(); max = rule.getDoMax(); break;
            case SensorReading.METRIC_TEMP:
                min = rule.getTempMin(); max = rule.getTempMax(); break;
            default:
                throw new IllegalArgumentException("未知指标类型：" + metricType);
        }
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private SensorQuarantine quarantine(String deviceCode, String requestId, String signature,
                                        String rawBody, String reason) {
        SensorQuarantine quarantine = new SensorQuarantine();
        quarantine.setDeviceCode(deviceCode);
        quarantine.setRequestId(requestId);
        quarantine.setSignature(signature);
        quarantine.setRawPayload(rawBody);
        quarantine.setReason(reason);
        quarantine.setReceivedTime(LocalDateTime.now());
        quarantineMapper.insert(quarantine);
        evidenceService.record(SensorChangeEvidence.ENTITY_QUARANTINE, quarantine.getId(),
                SensorChangeEvidence.ACTION_QUARANTINE, requestId, null, quarantine,
                "上传包隔离：" + reason + "（原始报文已保留）");
        return quarantine;
    }

    private Map<String, Object> quarantineResult(SensorQuarantine quarantine) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", SensorUploadBatch.STATUS_QUARANTINED);
        result.put("quarantineId", quarantine.getId());
        result.put("reason", quarantine.getReason());
        return result;
    }

    private Map<String, Object> mergeResult(SensorUploadBatch batch, boolean replay) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batch.getId());
        result.put("requestId", batch.getRequestId());
        result.put("status", batch.getStatus());
        result.put("totalCount", batch.getTotalCount());
        result.put("mergedCount", batch.getMergedCount());
        result.put("duplicateCount", batch.getDuplicateCount());
        result.put("lateRejectedCount", batch.getLateRejectedCount());
        result.put("abnormalCount", batch.getAbnormalCount());
        result.put("ruleVersionId", batch.getRuleVersionId());
        result.put("replay", replay);
        if (replay) {
            List<Map<String, Object>> lateItems = new ArrayList<>();
            List<SensorReading> lates = readingMapper.selectList(new QueryWrapper<SensorReading>()
                    .eq("batch_id", batch.getId())
                    .eq("status", SensorReading.STATUS_LATE_REJECTED)
                    .orderByAsc("id"));
            for (SensorReading reading : lates) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("metricType", reading.getMetricType());
                item.put("deviceTime", reading.getDeviceTime().toString());
                item.put("seqNo", reading.getSeqNo());
                item.put("reason", reading.getDeviceTime().toLocalDate() + "「" + reading.getMetricType()
                        + "」日报已审核，迟到数据只能补充未审核区间");
                lateItems.add(item);
            }
            result.put("lateRejected", lateItems);
        }
        return result;
    }

    /**
     * 签名校验：HMAC-SHA256(设备密钥, requestId + "\n" + 原始报文) 的十六进制小写串。
     */
    private boolean signatureOk(String secretKey, String requestId, String rawBody, String signature) {
        if (signature == null || signature.trim().isEmpty()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((requestId + "\n" + rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equalsIgnoreCase(signature.trim());
        } catch (Exception ex) {
            return false;
        }
    }
}
