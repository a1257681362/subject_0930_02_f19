package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异常读数待复核队列：超范围读数入队，暂不计入日报。
 * 复核确认（CONFIRMED）补入未审核日报；复核剔除（EXCLUDED）永不计入。
 */
@TableName("t_sensor_review_task")
public class SensorReviewTask extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_EXCLUDED = "EXCLUDED";

    private Long readingId;
    private Long pondId;
    private String metricType;
    private LocalDateTime deviceTime;
    private BigDecimal readingValue;
    private String status;
    private String reviewedByName;
    private LocalDateTime reviewedTime;
    private String reviewRemark;

    public Long getReadingId() { return readingId; }
    public void setReadingId(Long readingId) { this.readingId = readingId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public LocalDateTime getDeviceTime() { return deviceTime; }
    public void setDeviceTime(LocalDateTime deviceTime) { this.deviceTime = deviceTime; }
    public BigDecimal getReadingValue() { return readingValue; }
    public void setReadingValue(BigDecimal readingValue) { this.readingValue = readingValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
}
