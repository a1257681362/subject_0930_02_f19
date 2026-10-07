package com.evops.aqua.growth.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保存（发布）设备校正系数新版本：按设备编码 + 版本号保存体重/体长校正系数。
 * 同设备旧 ACTIVE 版本置 SUPERSEDED；已冻结在历史任务上的版本快照不受影响、不重算。
 * requestId 幂等：重复提交返回首次版本，不产生第二条。
 */
public class CoefficientSaveRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    private String deviceName;

    @NotBlank(message = "系数版本号不能为空")
    @Size(max = 32, message = "系数版本号最长32位")
    private String versionNo;

    @NotNull(message = "体重校正系数不能为空")
    @DecimalMin(value = "0.0001", message = "体重校正系数必须大于0")
    private BigDecimal weightCoeff;

    @NotNull(message = "体长校正系数不能为空")
    @DecimalMin(value = "0.0001", message = "体长校正系数必须大于0")
    private BigDecimal lengthCoeff;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    private String requestId;

    private String remark;

    private String operator;

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
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
