package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;

/**
 * 收货点登记请求；requestId 幂等，重复登记返回首条。
 */
public class PointRegisterRequest {

    @NotBlank(message = "收货点编号不能为空")
    private String pointNo;

    @NotBlank(message = "收货点名称不能为空")
    private String pointName;

    private String address;
    private String contactPerson;
    private String contactPhone;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getPointNo() { return pointNo; }
    public void setPointNo(String pointNo) { this.pointNo = pointNo; }
    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
