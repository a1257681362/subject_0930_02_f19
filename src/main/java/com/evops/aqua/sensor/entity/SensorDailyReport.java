package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 传感器日报：按（池, 日, 指标）一行，统计由归并读数重算得出，
 * 重新上传不会重复计入。OPEN 未审核可随补录重算；
 * REVIEWED 已审核锁定，迟到数据一律拒绝补充。
 */
@TableName("t_sensor_daily_report")
public class SensorDailyReport extends BaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_REVIEWED = "REVIEWED";

    private Long pondId;
    private LocalDate reportDate;
    private String metricType;
    private Integer sampleCount;
    private BigDecimal avgValue;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String status;
    private Long ruleVersionId;
    private String reviewedByName;
    private LocalDateTime reviewedTime;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public BigDecimal getAvgValue() { return avgValue; }
    public void setAvgValue(BigDecimal avgValue) { this.avgValue = avgValue; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
}
