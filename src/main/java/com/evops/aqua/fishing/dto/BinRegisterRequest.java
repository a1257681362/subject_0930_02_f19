package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 库位登记请求：一仓对应一个规格；requestId 幂等，重复登记返回首次记录。
 */
public class BinRegisterRequest {

    @NotBlank(message = "库位编号不能为空")
    private String binNo;

    @NotBlank(message = "对应规格不能为空")
    private String specCode;

    @NotNull(message = "容量不能为空")
    private BigDecimal capacityKg;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getBinNo() { return binNo; }
    public void setBinNo(String binNo) { this.binNo = binNo; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
