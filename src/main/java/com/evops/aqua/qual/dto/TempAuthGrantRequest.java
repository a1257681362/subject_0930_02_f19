package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 临时授权请求：必须带结束时间（effectiveTo）与审批单号（approvalTicket）；requestId 幂等。
 */
public class TempAuthGrantRequest {

    @NotBlank(message = "人员编号不能为空")
    private String personCode;
    @NotBlank(message = "临时权限码不能为空")
    private String permCodes;
    private LocalDateTime effectiveFrom;
    @NotNull(message = "临时授权必须带结束时间")
    private LocalDateTime effectiveTo;
    @NotBlank(message = "临时授权必须带审批单号")
    private String approvalTicket;
    private String reason;
    @NotBlank(message = "操作者不能为空")
    private String operator;
    @NotBlank(message = "幂等请求号不能为空")
    private String requestId;

    public String getPersonCode() { return personCode; }
    public void setPersonCode(String personCode) { this.personCode = personCode; }
    public String getPermCodes() { return permCodes; }
    public void setPermCodes(String permCodes) { this.permCodes = permCodes; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getApprovalTicket() { return approvalTicket; }
    public void setApprovalTicket(String approvalTicket) { this.approvalTicket = approvalTicket; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
