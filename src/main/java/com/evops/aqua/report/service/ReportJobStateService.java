package com.evops.aqua.report.service;

import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.entity.ReportJob;
import com.evops.aqua.report.entity.ReportJobAttempt;
import com.evops.aqua.report.mapper.ReportJobAttemptMapper;
import com.evops.aqua.report.mapper.ReportJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 月报任务记账（独立 bean，保证 REQUIRES_NEW 经代理生效）：
 * 快照汇总事务回滚时，任务尝试明细、FAILED 终态、异常链路与失败证据必须独立提交保留。
 */
@Service
public class ReportJobStateService {

    private final ReportJobMapper jobMapper;
    private final ReportJobAttemptMapper attemptMapper;
    private final ReportEvidenceService evidenceService;

    public ReportJobStateService(ReportJobMapper jobMapper,
                                 ReportJobAttemptMapper attemptMapper,
                                 ReportEvidenceService evidenceService) {
        this.jobMapper = jobMapper;
        this.attemptMapper = attemptMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insertAttempt(ReportJobAttempt attempt) {
        attemptMapper.insert(attempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markAttemptSuccess(Long attemptId) {
        ReportJobAttempt update = new ReportJobAttempt();
        update.setId(attemptId);
        update.setStatus(ReportJobAttempt.STATUS_SUCCESS);
        update.setFinishedAt(LocalDateTime.now());
        attemptMapper.updateById(update);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markAttemptFailed(Long attemptId, Exception ex) {
        ReportJobAttempt update = new ReportJobAttempt();
        update.setId(attemptId);
        update.setStatus(ReportJobAttempt.STATUS_FAILED);
        update.setExceptionClass(ex.getClass().getName());
        update.setMessage(truncate(ex.getMessage(), 2000));
        update.setStackTrace(truncate(stackTraceOf(ex), 8000));
        update.setFinishedAt(LocalDateTime.now());
        attemptMapper.updateById(update);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markJobSuccess(ReportJob job, int attemptNo, Long reportId) {
        ReportJob update = new ReportJob();
        update.setId(job.getId());
        update.setStatus(ReportJob.STATUS_SUCCESS);
        update.setAttempts(attemptNo);
        update.setReportId(reportId);
        update.setFinishedTime(LocalDateTime.now());
        jobMapper.updateById(update);
        ReportJob fresh = jobMapper.selectById(job.getId());
        evidenceService.record(ReportChangeEvidence.ENTITY_JOB, job.getId(),
                ReportChangeEvidence.ACTION_JOB_SUCCESS, job.getRequestId(), job.getOperator(),
                null, fresh, "月报任务" + job.getJobNo() + "第" + attemptNo + "次尝试成功，快照ID=" + reportId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markJobFailed(ReportJob job, int attemptNo, Exception ex) {
        String message = truncate(ex.getMessage(), 2000);
        String chain = buildErrorChain(job.getId(), attemptNo, ex);
        ReportJob update = new ReportJob();
        update.setId(job.getId());
        update.setStatus(ReportJob.STATUS_FAILED);
        update.setAttempts(attemptNo);
        update.setLastError(message);
        update.setErrorChain(chain);
        update.setFinishedTime(LocalDateTime.now());
        jobMapper.updateById(update);
        ReportJob fresh = jobMapper.selectById(job.getId());
        evidenceService.record(ReportChangeEvidence.ENTITY_JOB, job.getId(),
                ReportChangeEvidence.ACTION_JOB_FAIL, job.getRequestId(), job.getOperator(),
                null, fresh, "月报任务" + job.getJobNo() + "第" + attemptNo + "次尝试失败：" + message
                        + "（异常链路已保留，等待重试）");
    }

    private String buildErrorChain(Long jobId, int currentAttemptNo, Exception ex) {
        StringBuilder sb = new StringBuilder();
        List<ReportJobAttempt> attempts = attemptMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReportJobAttempt>()
                        .eq("job_id", jobId).orderByAsc("attempt_no"));
        sb.append("异常链路（共").append(currentAttemptNo).append("次尝试）：\n");
        for (ReportJobAttempt a : attempts) {
            sb.append("#").append(a.getAttemptNo())
                    .append(" [").append(a.getStatus()).append("] ")
                    .append(a.getExceptionClass() == null ? "无异常" : a.getExceptionClass())
                    .append("：").append(a.getMessage() == null ? "" : a.getMessage())
                    .append('\n');
        }
        sb.append("本次异常：").append(ex.getClass().getName()).append('\n')
                .append(truncate(stackTraceOf(ex), 4000));
        return truncate(sb.toString(), 9000);
    }

    private String stackTraceOf(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
