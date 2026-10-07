package com.evops.aqua.release.dto;

import javax.validation.constraints.NotBlank;

/**
 * 授权签字人电子签名：工号 + 签名口令双因子核验，且该签字人必须处于授权状态。
 * 仅已提交（SUBMITTED）检验单可签名；签名后批次方可进入发货。重复签名幂等。
 */
public class ReleaseSignRequest {

    @NotBlank(message = "签字人工号不能为空")
    private String employeeNo;

    @NotBlank(message = "签名口令不能为空")
    private String signSecret;

    private String remark;

    private String requestId;

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getSignSecret() { return signSecret; }
    public void setSignSecret(String signSecret) { this.signSecret = signSecret; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
