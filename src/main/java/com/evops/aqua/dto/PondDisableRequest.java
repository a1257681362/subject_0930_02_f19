package com.evops.aqua.dto;

import javax.validation.constraints.NotBlank;

/**
 * 养殖池置为无效请求：停用原因必填，操作者可缺省（记为系统操作）。
 */
public class PondDisableRequest {

    @NotBlank(message = "停用原因不能为空")
    private String reason;

    private String operator;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
