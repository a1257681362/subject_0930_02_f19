package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 潮汐事件登记请求：按海区逐条登记潮汐表（高潮/低潮）。
 * 操作者必填；requestId 幂等，同一海区同一时刻同一潮型重复登记返回首次记录。
 */
public class TideEventRequest {

    @NotBlank(message = "海区不能为空")
    private String seaArea;

    @NotNull(message = "潮汐时刻不能为空")
    private LocalDateTime tideTime;

    @NotBlank(message = "潮型不能为空")
    private String tideType;

    private Integer tideHeightCm;

    private String source;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
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
