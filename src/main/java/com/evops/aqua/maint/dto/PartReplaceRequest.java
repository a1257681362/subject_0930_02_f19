package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 备件替换请求：新件序列号装机，旧件序列号强制留存。
 * 新件实例须在库（或本单已锁定），旧件序列号须为当前装在该设备上的实例。
 */
public class PartReplaceRequest {

    @NotBlank(message = "新件序列号不能为空")
    private String newSerialNo;
    @NotBlank(message = "旧件序列号不能为空——替换必须留存旧件序列号")
    private String removedSerialNo;
    @NotBlank(message = "换件人不能为空")
    private String replacedBy;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;
    private String remark;

    public String getNewSerialNo() { return newSerialNo; }
    public void setNewSerialNo(String newSerialNo) { this.newSerialNo = newSerialNo; }
    public String getRemovedSerialNo() { return removedSerialNo; }
    public void setRemovedSerialNo(String removedSerialNo) { this.removedSerialNo = removedSerialNo; }
    public String getReplacedBy() { return replacedBy; }
    public void setReplacedBy(String replacedBy) { this.replacedBy = replacedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
