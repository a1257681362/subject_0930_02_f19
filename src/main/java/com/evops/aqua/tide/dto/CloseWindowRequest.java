package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 窗口临时关闭请求：关闭原因必填。
 * 关闭后向窗口内受影响班组的排班逐条发送通知，班组签收后留回执。
 */
public class CloseWindowRequest {

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "关闭原因不能为空")
    private String reason;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
