package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 证书登记请求：certNo 全局唯一；requestId 幂等，重复登记返回首条。
 */
public class CertificateRegisterRequest {

    @NotBlank(message = "人员编号不能为空")
    private String personCode;
    @NotBlank(message = "证书编号不能为空")
    private String certNo;
    @NotBlank(message = "证书类型不能为空")
    private String certType;
    private LocalDate issuedAt;
    @NotNull(message = "证书到期日不能为空")
    private LocalDate expiresAt;
    @NotBlank(message = "操作者不能为空")
    private String operator;
    @NotBlank(message = "幂等请求号不能为空")
    private String requestId;

    public String getPersonCode() { return personCode; }
    public void setPersonCode(String personCode) { this.personCode = personCode; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public String getCertType() { return certType; }
    public void setCertType(String certType) { this.certType = certType; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
