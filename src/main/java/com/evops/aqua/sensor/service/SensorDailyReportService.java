package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.mapper.SensorDailyReportMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 传感器日报：按（池, 日, 指标）一行，统计由归并读数重算得出。
 * 归并自然键为（池塘, 指标, 设备时间, 序号）——设备更换不断链、重复补传（含跨设备、跨包、
 * 乱序、并发）不会产生第二行，日报样本数与最小/最大值不会被重复放大；聚合时再按自然键
 * 去重一道，双保险。OPEN 未审核可随补录重算；REVIEWED 已审核锁定，迟到数据一律拒绝补充。
 * 日报首次建行后冻结口径规则版本：即便离线归并规则先发生变更，已存在日报仍按原版本完成
 * 核对，不随新版本漂移；版本不一致仅追加 WATCH_GUARD 凭证，不覆盖既有证据。
 */
@Service
public class SensorDailyReportService {

    private final SensorDailyReportMapper reportMapper;
    private final SensorReadingMapper readingMapper;
    private final SensorEvidenceService evidenceService;
    private final SensorRiskWatchService riskWatchService;

    public SensorDailyReportService(SensorDailyReportMapper reportMapper,
                                    SensorReadingMapper readingMapper,
                                    SensorEvidenceService evidenceService,
                                    SensorRiskWatchService riskWatchService) {
        this.reportMapper = reportMapper;
        this.readingMapper = readingMapper;
        this.evidenceService = evidenceService;
        this.riskWatchService = riskWatchService;
    }

    /**
     * 重算某（池, 日, 指标）日报：统计口径为 NORMAL + 复核确认 CONFIRMED 的读数，
     * 按归并自然键（设备时间, 序号）去重后取样本数/均值/最小/最大。
     * 已 REVIEWED 的日报冻结不重算；已存在（含 OPEN）日报固定首次口径版本，不因新归并
     * 规则版本发布而漂移。首次归并该日时按当时规则版本建行。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDailyReport recalc(Long pondId, LocalDate reportDate, String metricType,
                                    Long ruleVersionId, String requestId) {
        SensorDailyReport report = find(pondId, reportDate, metricType);
        if (report != null && isFrozen(report)) {
            return report;
        }
        List<SensorReading> counted = countedReadings(pondId, reportDate, metricType);
        SensorDailyReport before = report == null ? null : copyOf(report);
        if (report == null) {
            report = new SensorDailyReport();
            report.setPondId(pondId);
            report.setReportDate(reportDate);
            report.setMetricType(metricType);
            report.setStatus(SensorDailyReport.STATUS_OPEN);
            report.setRuleVersionId(ruleVersionId);
            applyStats(report, counted);
            reportMapper.insert(report);
            evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                    SensorChangeEvidence.ACTION_CREATE, requestId, null, report,
                    "生成" + reportDate + "「" + metricType + "」日报，样本数=" + counted.size()
                            + "，冻结归并规则版本id=" + ruleVersionId);
        } else {
            // 离线归并规则可能已先变更：已存在日报始终按首次冻结的原版本核对，不漂移口径。
            if (ruleVersionId != null && !ruleVersionId.equals(report.getRuleVersionId())) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("reportId", report.getId());
                snapshot.put("reportDate", reportDate.toString());
                snapshot.put("metricType", metricType);
                snapshot.put("frozenRuleVersionId", report.getRuleVersionId());
                snapshot.put("incomingRuleVersionId", ruleVersionId);
                riskWatchService.recordGuard(requestId, snapshot,
                        "高风险观察项防护：离线归并规则已变更，" + reportDate + "「" + metricType
                                + "」日报仍按原冻结版本id=" + report.getRuleVersionId()
                                + "完成核对，不切换口径、不覆盖既有凭证");
            }
            applyStats(report, counted);
            reportMapper.updateById(report);
            evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                    SensorChangeEvidence.ACTION_RECALC, requestId, before, report,
                    "重算" + reportDate + "「" + metricType + "」日报，样本数=" + counted.size()
                            + "（重算自归并读数，自然键去重，重复/乱序/跨设备补传不重复计入；"
                            + "口径版本id=" + report.getRuleVersionId() + "）");
        }
        return report;
    }

    /**
     * 审核日报：区间锁定，此后落入该（池, 日, 指标）的迟到数据一律拒绝补充。
     * 幂等：已审核日报重复审核直接返回，不重复写证据。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDailyReport review(Long reportId, String reviewedByName, String requestId) {
        SensorDailyReport report = getOrThrow(reportId);
        if (SensorDailyReport.STATUS_REVIEWED.equals(report.getStatus())) {
            return report;
        }
        SensorDailyReport before = copyOf(report);
        report.setStatus(SensorDailyReport.STATUS_REVIEWED);
        report.setReviewedByName(reviewedByName);
        report.setReviewedTime(LocalDateTime.now());
        reportMapper.updateById(report);
        evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                SensorChangeEvidence.ACTION_REVIEW, requestId, before, report,
                "审核" + report.getReportDate() + "「" + report.getMetricType()
                        + "」日报，区间锁定，迟到数据不再补充；审核人：" + reviewedByName);
        return report;
    }

    public boolean isReviewed(Long pondId, LocalDate reportDate, String metricType) {
        SensorDailyReport report = find(pondId, reportDate, metricType);
        return report != null && isFrozen(report);
    }

    public SensorDailyReport find(Long pondId, LocalDate reportDate, String metricType) {
        List<SensorDailyReport> list = reportMapper.selectList(new QueryWrapper<SensorDailyReport>()
                .eq("pond_id", pondId)
                .eq("report_date", reportDate)
                .eq("metric_type", metricType));
        return list.isEmpty() ? null : list.get(0);
    }

    public SensorDailyReport getOrThrow(Long id) {
        SensorDailyReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("传感器日报不存在，id=" + id);
        }
        return report;
    }

    public List<SensorDailyReport> list(Long pondId, LocalDate reportDate, String metricType) {
        QueryWrapper<SensorDailyReport> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(reportDate != null, "report_date", reportDate);
        wrapper.eq(metricType != null && !metricType.trim().isEmpty(), "metric_type", metricType);
        wrapper.orderByAsc("report_date", "metric_type");
        return reportMapper.selectList(wrapper);
    }

    private boolean isFrozen(SensorDailyReport report) {
        return SensorDailyReport.STATUS_REVIEWED.equals(report.getStatus());
    }

    /**
     * 取某日计入日报的读数（NORMAL + CONFIRMED）。跨日边界采用半开区间
     * [当日 00:00, 次日 00:00)：次日 00:00 整的读数归入次日，23:59:59.x 归入当日，
     * 边界读数不可能被两日同时统计。隔离包不产生读数，天然不进入本查询。
     */
    private List<SensorReading> countedReadings(Long pondId, LocalDate reportDate, String metricType) {
        LocalDateTime dayStart = reportDate.atStartOfDay();
        LocalDateTime dayEnd = reportDate.plusDays(1).atStartOfDay();
        List<SensorReading> rows = readingMapper.selectList(new QueryWrapper<SensorReading>()
                .eq("pond_id", pondId)
                .eq("metric_type", metricType)
                .ge("device_time", dayStart)
                .lt("device_time", dayEnd)
                .in("status", SensorReading.STATUS_NORMAL, SensorReading.STATUS_CONFIRMED)
                .orderByAsc("device_time", "seq_no", "id"));
        return distinctBySlot(rows);
    }

    /**
     * 按归并自然键（设备时间, 序号）去重。正常情况下数据库唯一约束已保证一槽位一行，
     * 这里是聚合链路的第二道防线：即便底层出现同槽位多行（历史数据/绕过归并写入），
     * 日报也绝不重复放大样本数与最小/最大值；查询按 id 升序，保留最早归并那条（首次为准）。
     */
    private List<SensorReading> distinctBySlot(List<SensorReading> rows) {
        Set<String> seenSlots = new LinkedHashSet<>();
        List<SensorReading> distinct = new ArrayList<>();
        for (SensorReading row : rows) {
            if (seenSlots.add(row.getDeviceTime() + "|" + row.getSeqNo())) {
                distinct.add(row);
            }
        }
        return distinct;
    }

    private void applyStats(SensorDailyReport report, List<SensorReading> counted) {
        report.setSampleCount(counted.size());
        if (counted.isEmpty()) {
            report.setAvgValue(null);
            report.setMinValue(null);
            report.setMaxValue(null);
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        for (SensorReading reading : counted) {
            BigDecimal value = reading.getReadingValue();
            sum = sum.add(value);
            min = min == null || value.compareTo(min) < 0 ? value : min;
            max = max == null || value.compareTo(max) > 0 ? value : max;
        }
        report.setAvgValue(sum.divide(BigDecimal.valueOf(counted.size()), 3, RoundingMode.HALF_UP));
        report.setMinValue(min);
        report.setMaxValue(max);
    }

    private SensorDailyReport copyOf(SensorDailyReport source) {
        SensorDailyReport copy = new SensorDailyReport();
        copy.setId(source.getId());
        copy.setPondId(source.getPondId());
        copy.setReportDate(source.getReportDate());
        copy.setMetricType(source.getMetricType());
        copy.setSampleCount(source.getSampleCount());
        copy.setAvgValue(source.getAvgValue());
        copy.setMinValue(source.getMinValue());
        copy.setMaxValue(source.getMaxValue());
        copy.setStatus(source.getStatus());
        copy.setRuleVersionId(source.getRuleVersionId());
        copy.setReviewedByName(source.getReviewedByName());
        copy.setReviewedTime(source.getReviewedTime());
        return copy;
    }
}
