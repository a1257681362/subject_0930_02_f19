package com.evops.aqua.growth.dto;

import java.time.LocalDate;

/**
 * 按方案生成测量任务：区间缺省取方案区间，按测量频次逐测量日生成。
 * 幂等：同方案同池同批同日只生成一次，重复请求返回已有任务。
 */
public class GrowthTaskGenerateRequest {

    private LocalDate startDate;
    private LocalDate endDate;
    private String requestId;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
