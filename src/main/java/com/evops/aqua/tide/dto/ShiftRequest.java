package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 人员班次登记请求：班组值勤时段。
 * 候选窗口须被某班组班次全覆盖（闭区间，端点相等算覆盖）才可执行。
 */
public class ShiftRequest {

    @NotBlank(message = "班组不能为空")
    private String crewName;

    @NotNull(message = "班次开始时间不能为空")
    private LocalDateTime shiftStart;

    @NotNull(message = "班次结束时间不能为空")
    private LocalDateTime shiftEnd;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    private String remark;

    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public LocalDateTime getShiftStart() { return shiftStart; }
    public void setShiftStart(LocalDateTime shiftStart) { this.shiftStart = shiftStart; }
    public LocalDateTime getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(LocalDateTime shiftEnd) { this.shiftEnd = shiftEnd; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
