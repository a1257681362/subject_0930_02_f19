package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 岗位可操作范围：menu_codes / api_perms 逗号分隔权限码（menu:* / api:*，api 支持 /** 前缀匹配）。
 * required_cert_type 为上岗必须持有的有效证书类型——证书无效或过期即收回该岗位全部菜单与接口权限。
 */
@TableName("t_qual_role_scope")
public class RoleScope extends BaseEntity {

    private String roleCode;
    private String roleName;
    private String menuCodes;
    private String apiPerms;
    private String requiredCertType;
    private String updatedBy;
    private String remark;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getMenuCodes() { return menuCodes; }
    public void setMenuCodes(String menuCodes) { this.menuCodes = menuCodes; }
    public String getApiPerms() { return apiPerms; }
    public void setApiPerms(String apiPerms) { this.apiPerms = apiPerms; }
    public String getRequiredCertType() { return requiredCertType; }
    public void setRequiredCertType(String requiredCertType) { this.requiredCertType = requiredCertType; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
