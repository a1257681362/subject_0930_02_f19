package com.evops.aqua.release.dto;

import javax.validation.constraints.NotBlank;

/**
 * 实验室提交检验轮次：每个必检指标必须已录入数值，系统按阈值闭区间比对并生成放行建议，
 * 同时为该轮样品签发与其一一对应的检测报告。重复提交幂等，不重复出报告。
 */
public class ReleaseRoundSubmitRequest {

    @NotBlank(message = "送检/录入人不能为空")
    private String submittedBy;

    private String requestId;

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
