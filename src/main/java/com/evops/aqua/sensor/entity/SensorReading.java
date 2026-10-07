package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 归并读数：按（设备, 指标, 设备时间, 序号）自然键归并去重，
 * 重新上传同槽位读数以首次为准，不重复计入日报。
 * 归并时冻结规则版本与判定结论，新版本发布不回改旧读数。
 */
@TableName("t_sensor_reading")
public class SensorReading extends BaseEntity {

    /** 正常入报 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 异常待复核（暂不计入日报） */
    public static final String STATUS_ABNORMAL_PENDING = "ABNORMAL_PENDING";
    /** 复核确认有效（计入未审核日报） */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 复核剔除（永不计入日报） */
    public static final String STATUS_EXCLUDED = "EXCLUDED";
    /** 迟到被拒：落入已审核区间，留痕但不入报 */
    public static final String STATUS_LATE_REJECTED = "LATE_REJECTED";

    public static final String METRIC_SALINITY = "SALINITY";
    public static final String METRIC_DO = "DO";
    public static final String METRIC_TEMP = "TEMP";

    private Long batchId;
    private Long deviceId;
    private Long pondId;
    private String metricType;
    private LocalDateTime deviceTime;
    private Integer seqNo;
    private BigDecimal readingValue;
    private String status;
    private Long ruleVersionId;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public LocalDateTime getDeviceTime() { return deviceTime; }
    public void setDeviceTime(LocalDateTime deviceTime) { this.deviceTime = deviceTime; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public BigDecimal getReadingValue() { return readingValue; }
    public void setReadingValue(BigDecimal readingValue) { this.readingValue = readingValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
}
