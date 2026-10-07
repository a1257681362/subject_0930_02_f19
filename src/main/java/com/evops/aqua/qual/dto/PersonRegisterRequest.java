package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;

/**
 * 养殖人员登记请求：岗位 VET / SAMPLER / FISHER / EQUIPMENT_OPERATOR；requestId 幂等。
 */
public class PersonRegisterRequest {

    @NotBlank(message = "人员编号不能为空")
    private String personCode;
    @NotBlank(message = "人员姓名不能为空")
    private String personName;
    @NotBlank(message = "岗位不能为空")
    private String roleCode;
    private String phone;
    private String remark;
    @NotBlank(message = "幂等请求号不能为空")
    private String requestId;

    public String getPersonCode() { return personCode; }
    public void setPersonCode(String personCode) { this.personCode = personCode; }
    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
