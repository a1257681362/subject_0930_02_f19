package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采样任务：按池按日生成，冻结生成时的规则版本与阶段。
 * DRAFT 草稿（可反复暂存）→ SUBMITTED 已提交（缺测须说明原因）
 * → APPROVED 主管审核通过（进入健康曲线）/ REJECTED 审核驳回（退回草稿）。
 */
@TableName("t_wq_task")
public class WqTask extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String taskNo;
    private Long planId;
    private Long pondId;
    private LocalDate taskDate;
    private String stageCode;
    private Long ruleVersionId;
    private String status;
    private Long submittedBy;
    private LocalDateTime submittedTime;
    private Long reviewedBy;
    private LocalDateTime reviewedTime;
    private String reviewRemark;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public LocalDate getTaskDate() { return taskDate; }
    public void setTaskDate(LocalDate taskDate) { this.taskDate = taskDate; }
    public String getStageCode() { return stageCode; }
    public void setStageCode(String stageCode) { this.stageCode = stageCode; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(LocalDateTime submittedTime) { this.submittedTime = submittedTime; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
}
