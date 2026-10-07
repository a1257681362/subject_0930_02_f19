package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 养殖人员档案：岗位（VET 兽医 / SAMPLER 采样员 / FISHER 捕捞员 / EQUIPMENT_OPERATOR 设备员）
 * 决定基础可操作范围；状态 ACTIVE（在职）/ FROZEN（已冻结）/ LEFT（离职）。
 */
@TableName("t_qual_person")
public class QualPerson extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_LEFT = "LEFT";

    private String personCode;
    private String personName;
    private String roleCode;
    private String status;
    private String phone;
    private String remark;
    private String requestId;

    public String getPersonCode() { return personCode; }
    public void setPersonCode(String personCode) { this.personCode = personCode; }
    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
