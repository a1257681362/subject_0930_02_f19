package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运行小时登记：设备上报累计运行小时，只追加。运行小时计划据此推算到期。
 */
@TableName("t_maint_meter_reading")
public class MaintMeterReading extends BaseEntity {

    private Long deviceId;
    private BigDecimal readingHours;
    private LocalDate readingDate;
    private String operator;
    private String requestId;
    private String remark;

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public BigDecimal getReadingHours() { return readingHours; }
    public void setReadingHours(BigDecimal readingHours) { this.readingHours = readingHours; }
    public LocalDate getReadingDate() { return readingDate; }
    public void setReadingDate(LocalDate readingDate) { this.readingDate = readingDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
