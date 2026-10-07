package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;

/**
 * 替换收货点审批：审批人与审批意见缺一不可；requestId 幂等。
 */
public class PointChangeReviewRequest {

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
