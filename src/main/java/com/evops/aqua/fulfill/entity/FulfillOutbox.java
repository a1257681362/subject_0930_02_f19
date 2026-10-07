package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 海水养殖管理同步发件箱：
 * event_id 确定性派生（FULFILL_SPLIT#requestId），同一拆单事件全局只发一次（对端按 eventId 幂等）。
 * PENDING 待发送 → SENT 已确认；只有 PENDING 记录允许被发送，恢复后重扫 SENT 跳过——不重复通知。
 */
@TableName("t_fulfill_outbox")
public class FulfillOutbox extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String BIZ_SPLIT = "FULFILL_SPLIT";
    public static final String EVENT_PREFIX = "FULFILL_SPLIT#";

    private String eventId;
    private String bizType;
    private Long bizId;
    private String payload;
    private String status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime sentTime;
    private String requestId;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getSentTime() { return sentTime; }
    public void setSentTime(LocalDateTime sentTime) { this.sentTime = sentTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
