package com.evops.aqua.wq.dto;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 计划按池生成采样任务。日期范围缺省取计划区间。
 * 幂等：同一 planId + requestId（或同池同日）重复请求不会产生第二条任务。
 */
public class TaskGenerateRequest {

    @NotNull(message = "采样计划不能为空")
    private Long planId;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 幂等请求号：相同请求号重复生成直接返回已有任务，不再产生新任务 */
    private String requestId;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
