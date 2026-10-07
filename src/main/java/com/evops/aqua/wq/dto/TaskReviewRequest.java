package com.evops.aqua.wq.dto;

import javax.validation.constraints.NotNull;

/**
 * 主管审核：通过后已测值进入池塘健康曲线；驳回则退回草稿并说明意见。
 * 幂等：任务已处于目标状态时重复请求返回原任务，不重复写入曲线。
 */
public class TaskReviewRequest {

    @NotNull(message = "审核结论不能为空")
    private Boolean approved;
    private String remark;
    private String requestId;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
