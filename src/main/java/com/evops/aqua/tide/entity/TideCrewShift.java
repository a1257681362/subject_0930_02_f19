package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 人员班次：班组值勤时段。候选窗口须被某班组班次全覆盖才可执行。
 * 覆盖判定为闭区间：shift_start <= window_start 且 shift_end >= window_end，
 * 端点相等算覆盖（与重叠判定的半开口径配套，同属统一口径）。
 */
@TableName("t_tide_crew_shift")
public class TideCrewShift extends BaseEntity {

    private String crewName;
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    private String operator;
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
