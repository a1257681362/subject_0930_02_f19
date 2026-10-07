package com.evops.aqua.wq.dto;

import java.time.LocalDate;

/**
 * 修改采样计划（只允许改名称/日期/备注；阶段与规则版本在任务生成后即冻结）。
 * 字段为空表示不修改；所有修改保留证据。
 */
public class PlanUpdateRequest {

    private String planName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String remark;

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
