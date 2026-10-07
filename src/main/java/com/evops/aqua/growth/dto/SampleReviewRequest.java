package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;

/**
 * 对已录入样本补充/变更异常复核：只能改异常标记与复核理由，绝不删除样本。
 * abnormalityFlag=TRUE 时 reviewReason 必填；置回 FALSE（复核为正常）时清空理由并重新计入均值。
 */
public class SampleReviewRequest {

    private Boolean abnormalityFlag;

    private String reviewReason;

    @NotBlank(message = "复核人不能为空")
    private String reviewedBy;

    private String requestId;

    public Boolean getAbnormalityFlag() { return abnormalityFlag; }
    public void setAbnormalityFlag(Boolean abnormalityFlag) { this.abnormalityFlag = abnormalityFlag; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
