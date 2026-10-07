package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;

/**
 * 解除隔离请求：必须由兽医复核——复核兽医与复核意见缺一不可。
 * requestId 幂等，重复解除返回同一结果，不重复写数量账。
 */
public class ReleaseRequest {

    @NotBlank(message = "复核兽医不能为空")
    private String vetName;

    @NotBlank(message = "兽医复核意见不能为空")
    private String reviewRemark;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    private String remark;

    public String getVetName() { return vetName; }
    public void setVetName(String vetName) { this.vetName = vetName; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
