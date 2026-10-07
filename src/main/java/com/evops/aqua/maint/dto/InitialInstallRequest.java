package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 设备初始装机登记：设备建档时把在库备件实物装到设备上，作为资产初始序列号基线，
 * 后续保养换件时该序列号即被换下的旧件序列号。
 */
public class InitialInstallRequest {

    @NotBlank(message = "装机备件序列号不能为空")
    private String serialNo;
    @NotBlank(message = "登记人不能为空")
    private String operator;
    private String remark;

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
