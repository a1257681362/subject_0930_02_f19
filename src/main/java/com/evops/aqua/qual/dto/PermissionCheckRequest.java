package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;

/**
 * 权限核验请求：permission 为 menu:* 或 api:* 权限码（api 按 /** 前缀匹配）。
 */
public class PermissionCheckRequest {

    @NotBlank(message = "人员编号不能为空")
    private String personCode;
    @NotBlank(message = "权限码不能为空")
    private String permission;

    public String getPersonCode() { return personCode; }
    public void setPersonCode(String personCode) { this.personCode = personCode; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
