package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;

/**
 * 异常读数复核：decision 取 CONFIRMED（确认有效，补入未审核日报）
 * 或 EXCLUDED（剔除，永不计入日报）。复核人与复核意见必填。
 */
public class ReviewResolveRequest {

    @NotBlank(message = "复核结论不能为空（CONFIRMED/EXCLUDED）")
    private String decision;
    @NotBlank(message = "复核人不能为空")
    private String reviewedByName;
    @NotBlank(message = "复核意见不能为空")
    private String reviewRemark;
    private String requestId;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
