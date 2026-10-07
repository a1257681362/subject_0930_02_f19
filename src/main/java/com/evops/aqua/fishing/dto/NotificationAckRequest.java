package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;

/**
 * 客户通知签收请求：留回执（签收人/时间/回执备注）；重复签收不覆盖首次回执。
 */
public class NotificationAckRequest {

    @NotBlank(message = "签收人不能为空")
    private String ackBy;

    private String ackRemark;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
