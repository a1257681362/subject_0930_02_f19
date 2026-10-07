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
import java.util.List;

/**
 * 传感器日报：按（池, 日, 指标）一行，统计由归并读数重算得出——
 * 归并自然键按池塘槽位去重，重新上传/换设备补传都不会重复计入日报。
 * OPEN 未审核可随补录重算；REVIEWED 已审核锁定，迟到数据一律拒绝补充。
 * 日报一经建立即冻结规则版本：即便离线归并规则版本先发生变更，
 * 后续重算仍按建报时的原版本核对，新版本只影响新建日报。
 * 高风险观察期间未审核日报的汇总重算同步停止（独立事务留证后拒绝）。
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
     * 重算某（池, 日, 指标）日报：统计口径为 NORMAL + 复核确认 CONFIRMED 的读数。
     * 已 REVIEWED 的日报冻结不重算（无需过观察闸门）。首次归并该日时按当时规则版本建行；
     * 已存在的日报保留建报时冻结的规则版本，不因入参版本变更而换版核对。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDailyReport recalc(Long pondId, LocalDate reportDate, String metricType,
                                    Long ruleVersionId, String requestId) {
        SensorDailyReport report = find(pondId, reportDate, metricType);
        if (report != null && isFrozen(report)) {
            return report;
        }
        // 高风险观察闸门：未审核日报的汇总重算同步限制（REVIEWED 已在上方冻结返回）。
        riskWatchService.requireAggregationAllowed(pondId, reportDate, metricType, requestId);
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
                            + "，冻结规则版本id=" + ruleVersionId);
        } else {
            applyStats(report, counted);
            reportMapper.updateById(report);
            evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                    SensorChangeEvidence.ACTION_RECALC, requestId, before, report,
                    "重算" + reportDate + "「" + metricType + "」日报，样本数=" + counted.size()
                            + "（重算自归并读数，重复/换设备补传不重复计入；按建报原规则版本id="
                            + report.getRuleVersionId() + "核对，不随新版本换版）");
        }
        return report;
    }

    /**
     * 解除高风险观察后的补算：把全部 OPEN 日报按各自冻结的原规则版本口径重算一遍，
     * 使观察期间确认但未汇总的读数补入；REVIEWED 日报继续冻结不回改。
     * 返回实际重算的日报条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalcAllOpenAfterWatchOff(String requestId) {
        List<SensorDailyReport> openReports = reportMapper.selectList(new QueryWrapper<SensorDailyReport>()
                .eq("status", SensorDailyReport.STATUS_OPEN)
                .orderByAsc("report_date", "metric_type", "pond_id"));
        for (SensorDailyReport open : openReports) {
            recalc(open.getPondId(), open.getReportDate(), open.getMetricType(),
                    open.getRuleVersionId(), requestId);
        }
        return openReports.size();
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

    private List<SensorReading> countedReadings(Long pondId, LocalDate reportDate, String metricType) {
        LocalDateTime dayStart = reportDate.atStartOfDay();
        LocalDateTime dayEnd = reportDate.plusDays(1).atStartOfDay();
        return readingMapper.selectList(new QueryWrapper<SensorReading>()
                .eq("pond_id", pondId)
                .eq("metric_type", metricType)
                .ge("device_time", dayStart)
                .lt("device_time", dayEnd)
                .in("status", SensorReading.STATUS_NORMAL, SensorReading.STATUS_CONFIRMED)
                .orderByAsc("device_time", "seq_no"));
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
