package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 禁作天气登记请求：按海区登记禁作时段。
 * 与候选窗口按半开区间 [start, end) 判定重叠，端点相接不算重叠。
 */
public class WeatherBanRequest {

    @NotBlank(message = "海区不能为空")
    private String seaArea;

    @NotNull(message = "禁作开始时间不能为空")
    private LocalDateTime banStart;

    @NotNull(message = "禁作结束时间不能为空")
    private LocalDateTime banEnd;

    @NotBlank(message = "天气类型不能为空")
    private String weatherType;

    private String reason;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public LocalDateTime getBanStart() { return banStart; }
    public void setBanStart(LocalDateTime banStart) { this.banStart = banStart; }
    public LocalDateTime getBanEnd() { return banEnd; }
    public void setBanEnd(LocalDateTime banEnd) { this.banEnd = banEnd; }
    public String getWeatherType() { return weatherType; }
    public void setWeatherType(String weatherType) { this.weatherType = weatherType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
