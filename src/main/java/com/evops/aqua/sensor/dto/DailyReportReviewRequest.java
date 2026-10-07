package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;

/**
 * 日报审核：审核后区间锁定，迟到数据一律拒绝补充。审核人必填。
 */
public class DailyReportReviewRequest {

    @NotBlank(message = "审核人不能为空")
    private String reviewedByName;
    private String requestId;

    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
