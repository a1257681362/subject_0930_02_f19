package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 替换收货点审批单：PENDING → APPROVED / REJECTED。
 * 审批人 + 审批意见缺一不可；通过后同事务只改子单收货点，数量与金额一律不动。
 */
@TableName("t_fulfill_point_change")
public class DeliveryPointChange extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String changeNo;
    private Long deliveryId;
    private Long fromPointId;
    private Long toPointId;
    private String reason;
    private String status;
    private String submittedBy;
    private String approvedBy;
    private String approveRemark;
    private LocalDateTime approvedTime;
    private String requestId;

    public String getChangeNo() { return changeNo; }
    public void setChangeNo(String changeNo) { this.changeNo = changeNo; }
    public Long getDeliveryId() { return deliveryId; }
    public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
    public Long getFromPointId() { return fromPointId; }
    public void setFromPointId(Long fromPointId) { this.fromPointId = fromPointId; }
    public Long getToPointId() { return toPointId; }
    public void setToPointId(Long toPointId) { this.toPointId = toPointId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
    public LocalDateTime getApprovedTime() { return approvedTime; }
    public void setApprovedTime(LocalDateTime approvedTime) { this.approvedTime = approvedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
