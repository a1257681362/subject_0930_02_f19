package com.evops.aqua.release.dto;

import javax.validation.constraints.NotBlank;

/**
 * 登记授权签字人：工号唯一，登记签名口令用于电子签名时核验。
 */
public class ReleaseSignerRequest {

    @NotBlank(message = "签字人工号不能为空")
    private String employeeNo;

    @NotBlank(message = "签字人姓名不能为空")
    private String signerName;

    @NotBlank(message = "签名口令不能为空")
    private String signSecret;

    private Boolean authorizedFlag;
    private String remark;
    private String requestId;

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public String getSignSecret() { return signSecret; }
    public void setSignSecret(String signSecret) { this.signSecret = signSecret; }
    public Boolean getAuthorizedFlag() { return authorizedFlag; }
    public void setAuthorizedFlag(Boolean authorizedFlag) { this.authorizedFlag = authorizedFlag; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
