package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;

/**
 * 客户签收请求：首签有效，重复签收返回首次回执、不覆盖签收人/时间/备注。
 */
public class HandoverSignRequest {

    @NotBlank(message = "签收人不能为空")
    private String signBy;

    private String signRemark;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getSignBy() { return signBy; }
    public void setSignBy(String signBy) { this.signBy = signBy; }
    public String getSignRemark() { return signRemark; }
    public void setSignRemark(String signRemark) { this.signRemark = signRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
