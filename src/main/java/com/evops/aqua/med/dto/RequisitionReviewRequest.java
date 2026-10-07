package com.evops.aqua.med.dto;

import javax.validation.constraints.NotBlank;

/**
 * 领用单审核请求（通过/驳回共用）：审批人与审批意见缺一不可——
 * 缺任一即参数校验失败，领用单停留待审核（PENDING），不落任何库存账。
 */
public class RequisitionReviewRequest {

    @NotBlank(message = "审批人不能为空")
    private String approvedBy;

    @NotBlank(message = "审批意见不能为空")
    private String approveRemark;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
