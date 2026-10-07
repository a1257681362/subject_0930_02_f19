package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 月报检验结论快照行：当月台账抽检与放行检验逐笔结论原样固化。
 * 检验结论是逐笔事实，不参与"缺采样填零"，无检验时该指标整体标 PENDING_FILL。
 */
@TableName("t_report_monthly_inspection")
public class ReportMonthlyInspection extends BaseEntity {

    public static final String SOURCE_LEDGER = "LEDGER_INSPECTION";
    public static final String SOURCE_RELEASE_ROUND = "RELEASE_ROUND";

    private Long reportId;
    private String sourceTable;
    private Long sourceId;
    private Long pondId;
    private Long batchId;
    private LocalDate inspectDate;
    private String conclusion;
    private String detail;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getInspectDate() { return inspectDate; }
    public void setInspectDate(LocalDate inspectDate) { this.inspectDate = inspectDate; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
