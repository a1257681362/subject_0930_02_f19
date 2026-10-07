package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 临时授权：岗位范围之外的临时权限，必须带结束时间与审批单号。
 * 仅在 [effectiveFrom, effectiveTo) 内生效，到点自动失效（实时判定）；
 * 扫描把已过结束时间的授权置为 EXPIRED 并留证。
 */
@TableName("t_qual_temp_auth")
public class TempAuth extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    private Long personId;
    private String permCodes;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String approvalTicket;
    private String reason;
    private String status;
    private String grantedBy;
    private String requestId;

    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
