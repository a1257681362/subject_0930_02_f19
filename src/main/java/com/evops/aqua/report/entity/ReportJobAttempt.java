package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 任务尝试链路明细：首次执行与每次重试各一行，
 * 逐次保留异常类型、消息与堆栈——失败任务的异常链路不因重试成功而清除。
 */
@TableName("t_report_job_attempt")
public class ReportJobAttempt extends BaseEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private Long jobId;
    private Integer attemptNo;
    private String status;
    private String exceptionClass;
    private String message;
    private String stackTrace;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
