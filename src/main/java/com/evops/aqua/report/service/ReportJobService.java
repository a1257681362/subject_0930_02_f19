package com.evops.aqua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.entity.ReportJob;
import com.evops.aqua.report.entity.ReportJobAttempt;
import com.evops.aqua.report.entity.ReportMonthly;
import com.evops.aqua.report.mapper.ReportJobAttemptMapper;
import com.evops.aqua.report.mapper.ReportJobMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月报生成任务编排：
 * - 手工触发与 Quartz 扫描都先建任务行，每次尝试（含首次）写 attempt 明细；
 * - 汇总在独立事务执行：成功固化新版本快照并回填任务；失败整体回滚不留半成品，
 *   但任务终态 FAILED、attempts 次数、last_error 与完整异常链路（异常类型+消息+堆栈）由
 *   ReportJobStateService 以 REQUIRES_NEW 独立事务保留，永不删除；
 * - 重试同一任务：attempts 递增、链路逐次追加；成功后任务转 SUCCESS（历史失败尝试不清除）。
 */
@Service
public class ReportJobService {

    private final ReportJobMapper jobMapper;
    private final ReportJobAttemptMapper attemptMapper;
    private final ReportSnapshotService snapshotService;
    private final ReportEvidenceService evidenceService;
    private final ReportJobStateService stateService;

    public ReportJobService(ReportJobMapper jobMapper,
                            ReportJobAttemptMapper attemptMapper,
                            ReportSnapshotService snapshotService,
                            ReportEvidenceService evidenceService,
                            ReportJobStateService stateService) {
        this.jobMapper = jobMapper;
        this.attemptMapper = attemptMapper;
        this.snapshotService = snapshotService;
        this.evidenceService = evidenceService;
        this.stateService = stateService;
    }

    /** 新建任务并立即执行一次（手工入口）。 */
    public ReportJob submitManual(String reportMonth, String seaArea, String requestId, String operator) {
        ReportJob job = createJob(reportMonth, seaArea, ReportMonthly.SOURCE_MANUAL, requestId, operator);
        return runJob(job);
    }

    /** Quartz 入口：为全部海区逐月（默认上月，可由参数覆盖）生成。 */
    public Map<String, Object> scheduledScan(String reportMonth) {
        List<String> areas = snapshotService.allSeaAreas();
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (String area : areas) {
            ReportJob job = createJob(reportMonth, area, ReportMonthly.SOURCE_QUARTZ,
                    null, ReportEvidenceService.OPERATOR_SCHEDULER);
            ReportJob finished = runJob(job);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seaArea", area);
            item.put("jobNo", job.getJobNo());
            item.put("status", finished.getStatus());
            item.put("attempts", finished.getAttempts());
            items.add(item);
            if (ReportJob.STATUS_SUCCESS.equals(finished.getStatus())) {
                success++;
            } else {
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportMonth", reportMonth);
        result.put("areaCount", areas.size());
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("items", items);
        return result;
    }

    /** Quartz MethodInvoking 入口（无参）：默认上个月。 */
    public void runScheduledScan() {
        scheduledScan(java.time.YearMonth.now().minusMonths(1).toString());
    }

    /**
     * 重试失败任务。只有 FAILED 任务可重试；每次重试 attempts+1 并追加一条 attempt 链路。
     * 不开事务：runJob 内快照事务独立回滚、失败记账独立提交。
     */
    public ReportJob retry(Long jobId) {
        ReportJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("月报生成任务不存在：" + jobId);
        }
        if (!ReportJob.STATUS_FAILED.equals(job.getStatus())) {
            throw new IllegalArgumentException("仅 FAILED 任务可重试，当前状态=" + job.getStatus());
        }
        evidenceService.recordStandalone(ReportChangeEvidence.ENTITY_JOB, job.getId(),
                ReportChangeEvidence.ACTION_RETRY, null, ReportEvidenceService.currentOperator(),
                copyOf(job), null,
                "人工重试月报任务" + job.getJobNo() + "（第" + (job.getAttempts() + 1) + "次尝试）");
        return runJob(job);
    }

    public ReportJob createJob(String reportMonth, String seaArea, String triggerSource,
                               String requestId, String operator) {
        ReportJob job = new ReportJob();
        job.setJobNo("RJOB-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        job.setReportMonth(reportMonth);
        job.setSeaArea(seaArea);
        job.setTriggerSource(triggerSource);
        job.setStatus(ReportJob.STATUS_RUNNING);
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setFiredTime(LocalDateTime.now());
        job.setOperator(operator);
        job.setRequestId(requestId);
        jobMapper.insert(job);
        return job;
    }

    /**
     * 执行一次尝试：汇总事务（ReportSnapshotService REQUIRED）独立提交/回滚，
     * 任务状态更新全部走 stateService 的 REQUIRES_NEW 独立事务（跨 bean 调用，代理生效）。
     */
    public ReportJob runJob(ReportJob jobInput) {
        ReportJob job = jobMapper.selectById(jobInput.getId());
        int attemptNo = (job.getAttempts() == null ? 0 : job.getAttempts()) + 1;
        ReportJobAttempt attempt = new ReportJobAttempt();
        attempt.setJobId(job.getId());
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(ReportJobAttempt.STATUS_RUNNING);
        attempt.setStartedAt(LocalDateTime.now());
        stateService.insertAttempt(attempt);

        try {
            ReportMonthly report = snapshotService.generateForArea(job.getReportMonth(), job.getSeaArea(),
                    job.getTriggerSource(), job.getId(), job.getOperator(), job.getRequestId());
            stateService.markAttemptSuccess(attempt.getId());
            stateService.markJobSuccess(job, attemptNo, report.getId());
        } catch (Exception ex) {
            stateService.markAttemptFailed(attempt.getId(), ex);
            stateService.markJobFailed(job, attemptNo, ex);
        }
        return jobMapper.selectById(job.getId());
    }

    public ReportJob getJob(Long jobId) {
        ReportJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("月报生成任务不存在：" + jobId);
        }
        return job;
    }

    public List<ReportJobAttempt> listAttempts(Long jobId) {
        return attemptMapper.selectList(new QueryWrapper<ReportJobAttempt>()
                .eq("job_id", jobId).orderByAsc("attempt_no"));
    }

    public List<ReportJob> listJobs(String status) {
        return jobMapper.selectList(new QueryWrapper<ReportJob>()
                .eq(status != null && !status.trim().isEmpty(), "status", status)
                .orderByDesc("id"));
    }

    private ReportJob copyOf(ReportJob source) {
        ReportJob copy = new ReportJob();
        copy.setId(source.getId());
        copy.setJobNo(source.getJobNo());
        copy.setStatus(source.getStatus());
        copy.setAttempts(source.getAttempts());
        copy.setLastError(source.getLastError());
        return copy;
    }
}
