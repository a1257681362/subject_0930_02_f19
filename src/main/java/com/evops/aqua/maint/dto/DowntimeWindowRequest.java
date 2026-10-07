package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 停机窗口登记请求：半开区间 [windowStart, windowEnd)，requestId 幂等。
 */
public class DowntimeWindowRequest {

    @NotNull(message = "窗口开始时间不能为空")
    private LocalDateTime windowStart;
    @NotNull(message = "窗口结束时间不能为空")
    private LocalDateTime windowEnd;
    private String reason;
    @NotBlank(message = "登记人不能为空")
    private String registeredBy;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
