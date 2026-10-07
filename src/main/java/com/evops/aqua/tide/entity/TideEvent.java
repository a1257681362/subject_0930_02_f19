package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 潮汐表：按海区逐条潮汐事件（高潮 HIGH / 低潮 LOW）。
 * 同一海区同一时刻同一潮型只一条（自然键幂等），重复登记返回首次记录。
 */
@TableName("t_tide_event")
public class TideEvent extends BaseEntity {

    public static final String TYPE_HIGH = "HIGH";
    public static final String TYPE_LOW = "LOW";

    private String seaArea;
    private LocalDateTime tideTime;
    private String tideType;
    private Integer tideHeightCm;
    private String source;
    private String operator;
    private String requestId;
    private String remark;

    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public LocalDateTime getTideTime() { return tideTime; }
    public void setTideTime(LocalDateTime tideTime) { this.tideTime = tideTime; }
    public String getTideType() { return tideType; }
    public void setTideType(String tideType) { this.tideType = tideType; }
    public Integer getTideHeightCm() { return tideHeightCm; }
    public void setTideHeightCm(Integer tideHeightCm) { this.tideHeightCm = tideHeightCm; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
