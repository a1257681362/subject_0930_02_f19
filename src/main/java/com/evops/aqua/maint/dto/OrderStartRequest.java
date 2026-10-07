package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 开工请求：开工停机属于同一操作并发敏感动作，带 requestId 与操作人，参与并发裁决。
 */
public class OrderStartRequest {

    @NotBlank(message = "开工操作人不能为空")
    private String operator;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
