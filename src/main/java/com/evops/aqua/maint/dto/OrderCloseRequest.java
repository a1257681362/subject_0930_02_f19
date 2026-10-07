package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 关闭工单/设备恢复运行请求：恢复运行是并发敏感动作，带 requestId 与操作人，参与并发裁决。
 */
public class OrderCloseRequest {

    @NotBlank(message = "关闭操作人不能为空")
    private String operator;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
