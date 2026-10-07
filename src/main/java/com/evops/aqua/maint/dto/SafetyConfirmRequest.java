package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 安全确认请求：对单个检查项逐项确认，确认人必填。
 * 设备恢复运行（工单关闭）前所有检查项必须全部 CONFIRMED。
 */
public class SafetyConfirmRequest {

    @NotBlank(message = "确认人不能为空")
    private String confirmedBy;
    private String remark;

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
