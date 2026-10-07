package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 自动顺延请求：系统自动挑选同海区下一个可执行窗口（设备空闲）生成新排班，
 * 原计划行的时间/池塘/设备一律不变，仅状态置 POSTPONED。
 * requestId 幂等：重复顺延返回首次生成的顺延排班。
 */
public class PostponeRequest {

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    private String reason;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
