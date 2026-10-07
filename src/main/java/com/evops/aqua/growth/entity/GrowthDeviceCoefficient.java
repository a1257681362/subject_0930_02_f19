package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备校正系数版本：按设备编码保存体重/体长校正系数。
 * 同一设备同一时刻仅一个 ACTIVE 版本；保存新版本时同设备旧 ACTIVE 置 SUPERSEDED。
 * 测量任务在生成时冻结版本指针——历史测量不随新系数重算。
 */
@TableName("t_growth_device_coefficient")
public class GrowthDeviceCoefficient extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String deviceCode;
    private String deviceName;
    private String versionNo;
    private BigDecimal weightCoeff;
    private BigDecimal lengthCoeff;
    private String status;
    private LocalDate effectiveDate;
    private String requestId;
    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public BigDecimal getWeightCoeff() { return weightCoeff; }
    public void setWeightCoeff(BigDecimal weightCoeff) { this.weightCoeff = weightCoeff; }
    public BigDecimal getLengthCoeff() { return lengthCoeff; }
    public void setLengthCoeff(BigDecimal lengthCoeff) { this.lengthCoeff = lengthCoeff; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
