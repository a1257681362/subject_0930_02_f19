package com.evops.aqua.feed.dto;

/**
 * 锁定/解锁/执行的可选幂等请求号。
 */
public class FeedOrderActionRequest {

    private String requestId;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
