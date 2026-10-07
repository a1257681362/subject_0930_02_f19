package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 收货点主数据：履约子单的交付目的地。替换收货点必须走审批。
 */
@TableName("t_fulfill_delivery_point")
public class DeliveryPoint extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String pointNo;
    private String pointName;
    private String address;
    private String contactPerson;
    private String contactPhone;
    private String status;
    private String operator;
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
