package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 隔离调度单：一张观察单至多调度出一张隔离单（observation_id 唯一）。
 * 解除隔离必须登记复核兽医（vetName）与复核意见，否则不允许解除。
 */
@TableName("t_disease_isolation")
public class DiseaseIsolation extends BaseEntity {

    public static final String STATUS_ISOLATED = "ISOLATED";
    public static final String STATUS_RELEASED = "RELEASED";

    private String isolationNo;
    private Long observationId;
    private Long batchId;
    private Long fromPondId;
    private Long toPondId;
    private Integer quantity;
    private String status;
    private String isolatedBy;
    private LocalDateTime isolatedTime;
    private String vetName;
    private String vetReviewRemark;
    private LocalDateTime releasedTime;
    private String requestId;
    private String releaseRequestId;
    private String remark;

    public String getIsolationNo() { return isolationNo; }
    public void setIsolationNo(String isolationNo) { this.isolationNo = isolationNo; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIsolatedBy() { return isolatedBy; }
    public void setIsolatedBy(String isolatedBy) { this.isolatedBy = isolatedBy; }
    public LocalDateTime getIsolatedTime() { return isolatedTime; }
    public void setIsolatedTime(LocalDateTime isolatedTime) { this.isolatedTime = isolatedTime; }
    public String getVetName() { return vetName; }
    public void setVetName(String vetName) { this.vetName = vetName; }
    public String getVetReviewRemark() { return vetReviewRemark; }
    public void setVetReviewRemark(String vetReviewRemark) { this.vetReviewRemark = vetReviewRemark; }
    public LocalDateTime getReleasedTime() { return releasedTime; }
    public void setReleasedTime(LocalDateTime releasedTime) { this.releasedTime = releasedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReleaseRequestId() { return releaseRequestId; }
    public void setReleaseRequestId(String releaseRequestId) { this.releaseRequestId = releaseRequestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
