package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 差异调整单：账面修正只能走差异单。PENDING 待审核 → APPROVED 才入余额；REJECTED 不动账。
 * 已结转（已出池）批次禁止建差异单。
 */
@TableName("t_cost_adjustment")
public class CostAdjustment extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String DIRECTION_ADD = "ADD";
    public static final String DIRECTION_SUB = "SUB";

    private String adjNo;
    private Long batchId;
    private Long pondId;
    private String costType;
    private String direction;
    private BigDecimal amount;
    private LocalDate occurDate;
    private Long voucherId;
    private String voucherNo;
    private String reason;
    private String status;
    private String applicant;
    private String reviewedBy;
    private String reviewRemark;
    private LocalDateTime reviewedTime;
    private String requestId;

    public String getAdjNo() { return adjNo; }
    public void setAdjNo(String adjNo) { this.adjNo = adjNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurDate() { return occurDate; }
    public void setOccurDate(LocalDate occurDate) { this.occurDate = occurDate; }
    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
