package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 替换收货点申请：必须填写原因；提交后停留 PENDING 等待审批。
 */
public class PointChangeSubmitRequest {

    @NotNull(message = "目标收货点不能为空")
    private Long toPointId;

    @NotBlank(message = "替换原因不能为空")
    private String reason;

    @NotBlank(message = "提交人不能为空")
    private String submittedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public Long getToPointId() { return toPointId; }
    public void setToPointId(Long toPointId) { this.toPointId = toPointId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
