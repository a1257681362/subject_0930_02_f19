package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 授权签字人：仅 authorizedFlag=TRUE 且签名口令核验通过者可对检验单电子签名放行。
 */
@TableName("t_rel_signer")
public class ReleaseSigner extends BaseEntity {

    private String employeeNo;
    private String signerName;
    private String signSecret;
    private Boolean authorizedFlag;
    private String remark;

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
}
