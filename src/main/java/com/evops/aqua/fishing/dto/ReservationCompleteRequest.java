package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;

/**
 * 预约完成请求：核对实际量与计划量，短装/超装自动生成差异单。
 */
public class ReservationCompleteRequest {

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
