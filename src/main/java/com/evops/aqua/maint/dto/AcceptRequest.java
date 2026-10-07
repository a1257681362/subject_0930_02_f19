package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 验收请求：验收人与验收意见缺一不可；只有 COMPLETED（维修完工待验收）工单可验收。
 * 未验收通过的工单不能关闭。
 */
public class AcceptRequest {

    @NotBlank(message = "验收人不能为空")
    private String acceptedBy;
    @NotBlank(message = "验收意见不能为空")
    private String acceptRemark;

    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }
    public String getAcceptRemark() { return acceptRemark; }
    public void setAcceptRemark(String acceptRemark) { this.acceptRemark = acceptRemark; }
}
