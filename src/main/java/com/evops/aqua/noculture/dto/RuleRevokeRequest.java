package com.evops.aqua.noculture.dto;

import javax.validation.constraints.NotBlank;

/**
 * 禁养规则撤销请求（requestId 强制幂等）。
 * 撤销后规则不再在投苗/转池/捕捞入口拦截，并对其冻结账逐行补偿解冻；
 * 批次仍被其他重叠生效规则冻结的，补偿记 SKIPPED_STILL_BOUND，保持冻结。
 */
public class RuleRevokeRequest {

    @NotBlank(message = "撤销人不能为空")
    private String operator;

    @NotBlank(message = "撤销原因不能为空")
    private String reason;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
