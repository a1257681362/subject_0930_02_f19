package com.evops.aqua.release.dto;

import javax.validation.constraints.NotBlank;

/**
 * 退回补检：仅已提交轮次可退回，原因与退回人缺一不可。
 * 退回不改写原轮次——原结论、原因、提交/退回操作时间全部冻结保留；
 * 系统另开一个复检轮次（round_no+1）与新样品，复检结果绝不覆盖初检。
 */
public class ReleaseReturnRequest {

    @NotBlank(message = "退回原因不能为空")
    private String reason;

    @NotBlank(message = "退回操作人不能为空")
    private String operator;

    private String requestId;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
