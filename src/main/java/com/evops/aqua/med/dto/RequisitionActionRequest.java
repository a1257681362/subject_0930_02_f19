package com.evops.aqua.med.dto;

import javax.validation.constraints.NotBlank;

/**
 * 领用单动作请求（提交/撤回共用）：操作者 + 幂等请求号。
 */
public class RequisitionActionRequest {

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
