package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 排班创建请求：把池塘与设备排入可执行窗口。
 * 校验窗口开放、池塘海区一致、设备可用；同一设备同一时间只能服务一口池。
 */
public class ScheduleCreateRequest {

    @NotNull(message = "换水窗口不能为空")
    private Long windowId;

    @NotNull(message = "池塘不能为空")
    private Long pondId;

    @NotNull(message = "换水设备不能为空")
    private Long deviceId;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    private String remark;

    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
