package com.evops.aqua.med.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用药领用单：关联池塘、病情（疫病观察单）与药品批号（明细行）。
 * 状态 DRAFT（草稿）/ PENDING（待审核）/ APPROVED（已审核）/ REJECTED（已驳回）/ WITHDRAWN（已撤回）。
 * 提交后计算休药期并锁定预计出池日；撤回只能撤销未审核单（DRAFT/PENDING）。
 */
@TableName("t_med_requisition")
public class MedRequisition extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private String reqNo;
    private Long pondId;
    private Long observationId;
    private String diseaseDesc;
    private LocalDate useDate;
    private BigDecimal waterVolumeM3;
    private String status;
    private String applicant;
    private String approvedBy;
    private String approveRemark;
    private LocalDateTime approvedTime;
    private String withdrawBy;
    private LocalDateTime withdrawTime;
    /** 版本号：首次建单为 1；撤回后每重提一次 +1。 */
    private Integer revNo;
    /** 版本链首张领用单编号：首单为自身编号，重提单指向首单。 */
    private String originReqNo;
    /** 重提单的上一版领用单 id；首单为空。 */
    private Long resubmitOfId;
    /** 重提时间：与撤回时间/审核时间分开落库，逐版可辨。 */
    private LocalDateTime resubmitTime;
    private String requestId;
    private String remark;

    public String getReqNo() { return reqNo; }
    public void setReqNo(String reqNo) { this.reqNo = reqNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public String getDiseaseDesc() { return diseaseDesc; }
    public void setDiseaseDesc(String diseaseDesc) { this.diseaseDesc = diseaseDesc; }
    public LocalDate getUseDate() { return useDate; }
    public void setUseDate(LocalDate useDate) { this.useDate = useDate; }
    public BigDecimal getWaterVolumeM3() { return waterVolumeM3; }
    public void setWaterVolumeM3(BigDecimal waterVolumeM3) { this.waterVolumeM3 = waterVolumeM3; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
    public LocalDateTime getApprovedTime() { return approvedTime; }
    public void setApprovedTime(LocalDateTime approvedTime) { this.approvedTime = approvedTime; }
    public String getWithdrawBy() { return withdrawBy; }
    public void setWithdrawBy(String withdrawBy) { this.withdrawBy = withdrawBy; }
    public LocalDateTime getWithdrawTime() { return withdrawTime; }
    public void setWithdrawTime(LocalDateTime withdrawTime) { this.withdrawTime = withdrawTime; }
    public Integer getRevNo() { return revNo; }
    public void setRevNo(Integer revNo) { this.revNo = revNo; }
    public String getOriginReqNo() { return originReqNo; }
    public void setOriginReqNo(String originReqNo) { this.originReqNo = originReqNo; }
    public Long getResubmitOfId() { return resubmitOfId; }
    public void setResubmitOfId(Long resubmitOfId) { this.resubmitOfId = resubmitOfId; }
    public LocalDateTime getResubmitTime() { return resubmitTime; }
    public void setResubmitTime(LocalDateTime resubmitTime) { this.resubmitTime = resubmitTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
