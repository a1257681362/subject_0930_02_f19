package com.evops.aqua.growth.dto;

/**
 * 测量员提交任务：DRAFT/REJECTED → SUBMITTED。
 * 提交前必须已录入样本，且每个异常样本都已填复核理由，否则拒绝。
 * requestId 幂等：已提交/已确认任务重复提交直接返回原任务。
 */
public class GrowthTaskSubmitRequest {

    private String requestId;
    private String remark;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
