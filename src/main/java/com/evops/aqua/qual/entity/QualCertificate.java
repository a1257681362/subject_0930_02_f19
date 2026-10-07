package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资质证书：状态 VALID（有效）/ EXPIRED（已过期）/ REVOKED（已吊销）。
 * 权限按 expires_at 与当前日期实时判定，过期当刻即收回，不等待扫描置位。
 */
@TableName("t_qual_certificate")
public class QualCertificate extends BaseEntity {

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    private Long personId;
    private String certNo;
    private String certType;
    private LocalDate issuedAt;
    private LocalDate expiresAt;
    private String status;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private String requestId;

    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public String getCertType() { return certType; }
    public void setCertType(String certType) { this.certType = certType; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
