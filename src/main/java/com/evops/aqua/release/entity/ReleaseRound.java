package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 检验轮次：初检 round_no=1，退回补检后逐轮追加（2、3…）。
 * 复检是新轮次，绝不覆盖初检轮；每轮的结论、退回原因、提交/退回操作时间独立冻结保留。
 */
@TableName("t_rel_round")
public class ReleaseRound extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_RETURNED = "RETURNED";

    private Long orderId;
    private Integer roundNo;
    private Long ruleVersionId;
    private String status;
    private String conclusion;
    private String conclusionDetail;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private String returnReason;
    private String returnOperator;
    private LocalDateTime returnedTime;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getConclusionDetail() { return conclusionDetail; }
    public void setConclusionDetail(String conclusionDetail) { this.conclusionDetail = conclusionDetail; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(LocalDateTime submittedTime) { this.submittedTime = submittedTime; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public String getReturnOperator() { return returnOperator; }
    public void setReturnOperator(String returnOperator) { this.returnOperator = returnOperator; }
    public LocalDateTime getReturnedTime() { return returnedTime; }
    public void setReturnedTime(LocalDateTime returnedTime) { this.returnedTime = returnedTime; }
}
