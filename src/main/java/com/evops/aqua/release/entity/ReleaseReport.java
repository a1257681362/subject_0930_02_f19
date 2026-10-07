package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 检测报告：与样品严格一一对应（uk_sample_id），报告编号全局唯一。
 * 初检报告在退回补检后原样保留——复检生成的是新轮次的新样品新报告，绝不改写本报告结论。
 */
@TableName("t_rel_report")
public class ReleaseReport extends BaseEntity {

    private Long sampleId;
    private Long orderId;
    private Long roundId;
    private String reportNo;
    private String conclusion;
    private String conclusionDetail;
    private String issuedBy;
    private LocalDateTime issuedTime;

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getConclusionDetail() { return conclusionDetail; }
    public void setConclusionDetail(String conclusionDetail) { this.conclusionDetail = conclusionDetail; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public LocalDateTime getIssuedTime() { return issuedTime; }
    public void setIssuedTime(LocalDateTime issuedTime) { this.issuedTime = issuedTime; }
}
