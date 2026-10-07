package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 水产监管月报快照头。
 * 同 月份+海区 每次成功（重新）生成产生新版本（version_seq 递增）；
 * frozen=TRUE 后整头及其指标/检验行永久固化，任何再生成只插新版本、绝不覆盖旧版本。
 */
@TableName("t_report_monthly")
public class ReportMonthly extends BaseEntity {

    public static final String STATUS_READY = "READY";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_QUARTZ = "QUARTZ";

    private String reportNo;
    private String reportMonth;
    private String seaArea;
    private Integer versionSeq;
    private Long caliberVersionId;
    private String caliberVersionNo;
    private String triggerSource;
    private String status;
    private Boolean frozen;
    private LocalDateTime frozenTime;
    private String frozenBy;
    private Integer pendingFillCount;
    private Long jobId;
    private String remark;

    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public String getReportMonth() { return reportMonth; }
    public void setReportMonth(String reportMonth) { this.reportMonth = reportMonth; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public Integer getVersionSeq() { return versionSeq; }
    public void setVersionSeq(Integer versionSeq) { this.versionSeq = versionSeq; }
    public Long getCaliberVersionId() { return caliberVersionId; }
    public void setCaliberVersionId(Long caliberVersionId) { this.caliberVersionId = caliberVersionId; }
    public String getCaliberVersionNo() { return caliberVersionNo; }
    public void setCaliberVersionNo(String caliberVersionNo) { this.caliberVersionNo = caliberVersionNo; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getFrozen() { return frozen; }
    public void setFrozen(Boolean frozen) { this.frozen = frozen; }
    public LocalDateTime getFrozenTime() { return frozenTime; }
    public void setFrozenTime(LocalDateTime frozenTime) { this.frozenTime = frozenTime; }
    public String getFrozenBy() { return frozenBy; }
    public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }
    public Integer getPendingFillCount() { return pendingFillCount; }
    public void setPendingFillCount(Integer pendingFillCount) { this.pendingFillCount = pendingFillCount; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
