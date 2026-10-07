package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;

/**
 * 岗位可操作范围配置请求：menu:/api: 权限码逗号分隔；requiredCertType 为上岗必须证书类型。
 */
public class RoleScopeUpdateRequest {

    private String menuCodes;
    private String apiPerms;
    private String requiredCertType;
    @NotBlank(message = "操作者不能为空")
    private String operator;
    private String remark;

    public String getMenuCodes() { return menuCodes; }
    public void setMenuCodes(String menuCodes) { this.menuCodes = menuCodes; }
    public String getApiPerms() { return apiPerms; }
    public void setApiPerms(String apiPerms) { this.apiPerms = apiPerms; }
    public String getRequiredCertType() { return requiredCertType; }
    public void setRequiredCertType(String requiredCertType) { this.requiredCertType = requiredCertType; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
