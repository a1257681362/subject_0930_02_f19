package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 禁作天气：按海区登记禁作时段。
 * 与候选窗口按半开区间 [start, end) 判定重叠——端点相接不算重叠（统一口径）。
 */
@TableName("t_tide_weather_ban")
public class TideWeatherBan extends BaseEntity {

    private String seaArea;
    private LocalDateTime banStart;
    private LocalDateTime banEnd;
    private String weatherType;
    private String reason;
    private String operator;
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
