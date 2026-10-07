package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;

/**
 * 主管确认 / 驳回。
 * approved=TRUE：任务 SUBMITTED → CONFIRMED，并写入/更新该任务唯一一条预测出池量；
 * approved=FALSE：驳回，必须填 remark（退回测量员重录重提，不产生预测）。
 * 预测只在确认时落定一次，历史任务不随新系数重算。
 */
public class TaskConfirmRequest {

    private Boolean approved = Boolean.TRUE;

    @NotBlank(message = "主管（确认人）不能为空")
    private String confirmedBy;

    private String remark;
    private String requestId;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
