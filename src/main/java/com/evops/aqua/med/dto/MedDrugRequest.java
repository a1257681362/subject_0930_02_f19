package com.evops.aqua.med.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 药品档案登记请求：requestId 强制幂等，operator 落证据。
 */
public class MedDrugRequest {

    @NotBlank(message = "药品编码不能为空")
    @Size(max = 32, message = "药品编码最长32位")
    private String drugCode;

    @NotBlank(message = "药品名称不能为空")
    @Size(max = 64, message = "药品名称最长64位")
    private String drugName;

    @Size(max = 64, message = "规格最长64位")
    private String spec;

    @NotBlank(message = "计量单位不能为空")
    @Size(max = 16, message = "计量单位最长16位")
    private String unit;

    @NotNull(message = "标准剂量不能为空")
    @DecimalMin(value = "0.0001", message = "标准剂量必须大于0")
    private BigDecimal dosagePerM3;

    @NotNull(message = "休药期天数不能为空")
    @DecimalMin(value = "0", message = "休药期天数不能为负")
    private Integer withdrawalDays;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getDrugCode() { return drugCode; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getDosagePerM3() { return dosagePerM3; }
    public void setDosagePerM3(BigDecimal dosagePerM3) { this.dosagePerM3 = dosagePerM3; }
    public Integer getWithdrawalDays() { return withdrawalDays; }
    public void setWithdrawalDays(Integer withdrawalDays) { this.withdrawalDays = withdrawalDays; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
