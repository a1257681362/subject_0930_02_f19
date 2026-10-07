package com.evops.aqua.feed.dto;

import javax.validation.constraints.NotBlank;

/**
 * 海水养殖管理权限收紧/放开请求。收紧须登记原因。
 */
public class AccessPolicyToggleRequest {

    @NotBlank(message = "调整饲料批号推进权限必须登记原因")
    private String reason;

    private String requestId;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
