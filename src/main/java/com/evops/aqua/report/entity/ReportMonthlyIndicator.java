package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 月报指标快照行：指标值与其统计口径版本同时固化。
 * dataStatus=NORMAL 时 indicatorValue 有值；PENDING_FILL 表示当月缺少采样数据，
 * 值必须留空并写 pendingReason——绝不允许以 0 冒充。
 */
@TableName("t_report_monthly_indicator")
public class ReportMonthlyIndicator extends BaseEntity {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_PENDING_FILL = "PENDING_FILL";

    private Long reportId;
    private String indicatorCode;
    private String indicatorName;
    private Long caliberVersionId;
    private String caliberVersionNo;
    private String caliberJson;
    private String indicatorValue;
    private String unit;
    private String dataStatus;
    private String pendingReason;
    private Integer sampleCount;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public Long getCaliberVersionId() { return caliberVersionId; }
    public void setCaliberVersionId(Long caliberVersionId) { this.caliberVersionId = caliberVersionId; }
    public String getCaliberVersionNo() { return caliberVersionNo; }
    public void setCaliberVersionNo(String caliberVersionNo) { this.caliberVersionNo = caliberVersionNo; }
    public String getCaliberJson() { return caliberJson; }
    public void setCaliberJson(String caliberJson) { this.caliberJson = caliberJson; }
    public String getIndicatorValue() { return indicatorValue; }
    public void setIndicatorValue(String indicatorValue) { this.indicatorValue = indicatorValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getDataStatus() { return dataStatus; }
    public void setDataStatus(String dataStatus) { this.dataStatus = dataStatus; }
    public String getPendingReason() { return pendingReason; }
    public void setPendingReason(String pendingReason) { this.pendingReason = pendingReason; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
}
