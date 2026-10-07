package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;

/**
 * 岗位变更请求：只追加历史，不删除任何历史操作记录；requestId 幂等。
 */
public class RoleChangeRequest {

    @NotBlank(message = "目标岗位不能为空")
    private String toRole;
    @NotBlank(message = "变更原因不能为空")
    private String reason;
    @NotBlank(message = "操作者不能为空")
    private String operator;
    @NotBlank(message = "幂等请求号不能为空")
    private String requestId;

    public String getToRole() { return toRole; }
    public void setToRole(String toRole) { this.toRole = toRole; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
