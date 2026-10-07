package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 排班改期请求：改窗口（可连同换设备）。
 * 待发布排班直接改；已发布排班必须登记调度审批人与审批意见，否则拒绝。
 */
public class RescheduleRequest {

    @NotNull(message = "目标窗口不能为空")
    private Long newWindowId;

    /** 新设备；为空沿用原设备。 */
    private Long newDeviceId;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    /** 调度审批人：排班已发布时必填。 */
    private String approver;

    /** 审批意见：排班已发布时必填。 */
    private String approveRemark;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public Long getNewWindowId() { return newWindowId; }
    public void setNewWindowId(Long newWindowId) { this.newWindowId = newWindowId; }
    public Long getNewDeviceId() { return newDeviceId; }
    public void setNewDeviceId(Long newDeviceId) { this.newDeviceId = newDeviceId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
