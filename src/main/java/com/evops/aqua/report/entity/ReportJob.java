package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 月报生成任务（手工/Quartz 各一条）。
 * 失败任务保留 FAILED 终态、attempts 重试次数与 errorChain 异常链路，永不删除；
 * 成功重试后转 SUCCESS 并关联新生成的新版本快照。
 */
@TableName("t_report_job")
public class ReportJob extends BaseEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String jobNo;
    private String reportMonth;
    private String seaArea;
    private String triggerSource;
    private String status;
    private Integer attempts;
    private Integer maxAttempts;
    private Long reportId;
    private String lastError;
    private String errorChain;
    private LocalDateTime firedTime;
    private LocalDateTime finishedTime;
    private String operator;
    private String requestId;

    public String getJobNo() { return jobNo; }
    public void setJobNo(String jobNo) { this.jobNo = jobNo; }
    public String getReportMonth() { return reportMonth; }
    public void setReportMonth(String reportMonth) { this.reportMonth = reportMonth; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getErrorChain() { return errorChain; }
    public void setErrorChain(String errorChain) { this.errorChain = errorChain; }
    public LocalDateTime getFiredTime() { return firedTime; }
    public void setFiredTime(LocalDateTime firedTime) { this.firedTime = firedTime; }
    public LocalDateTime getFinishedTime() { return finishedTime; }
    public void setFinishedTime(LocalDateTime finishedTime) { this.finishedTime = finishedTime; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
