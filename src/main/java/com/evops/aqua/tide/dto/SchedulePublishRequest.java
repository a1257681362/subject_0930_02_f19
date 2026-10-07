package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 排班发布请求：发布后排班对外生效，再修改必须经调度审批。
 */
public class SchedulePublishRequest {

    @NotBlank(message = "操作者不能为空")
    private String operator;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
