package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseChangeEvidence;
import com.evops.aqua.disease.entity.DiseaseFollowupTask;
import com.evops.aqua.disease.entity.DiseaseObservation;
import com.evops.aqua.disease.mapper.DiseaseFollowupTaskMapper;
import com.evops.aqua.disease.mapper.DiseaseObservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 疫病复查定时任务：调度系统按日扫描未结观察单（观察中/已隔离），逐单生成当日复查任务。
 * 幂等：task_no = FUP-{观察单id}-{yyyyMMdd} 唯一——定时任务重启或重复触发都不会多写第二条；
 * 操作者不丢：operator 固定为调度系统标识，source_observer 保留原观察人。
 */
@Service
public class DiseaseFollowupService {

    private final DiseaseFollowupTaskMapper followupTaskMapper;
    private final DiseaseObservationMapper observationMapper;
    private final DiseaseEvidenceService evidenceService;

    public DiseaseFollowupService(DiseaseFollowupTaskMapper followupTaskMapper,
                                  DiseaseObservationMapper observationMapper,
                                  DiseaseEvidenceService evidenceService) {
        this.followupTaskMapper = followupTaskMapper;
        this.observationMapper = observationMapper;
        this.evidenceService = evidenceService;
    }

    /** Quartz 定时入口：按当天日期扫描生成（经代理调用，事务生效）。 */
    @Transactional(rollbackFor = Exception.class)
    public void runScheduledScan() {
        generateDailyTasks(LocalDate.now());
    }

    /**
     * 按日生成复查任务：同观察单同日只生成一次，重跑（含重启后补跑）返回已存在任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateDailyTasks(LocalDate taskDate) {
        List<DiseaseObservation> open = observationMapper.selectList(
                new QueryWrapper<DiseaseObservation>()
                        .in("status", DiseaseObservation.STATUS_OBSERVING,
                                DiseaseObservation.STATUS_ISOLATED)
                        .orderByAsc("id"));
        List<DiseaseFollowupTask> created = new ArrayList<>();
        List<DiseaseFollowupTask> existed = new ArrayList<>();
        for (DiseaseObservation observation : open) {
            String taskNo = taskNo(observation.getId(), taskDate);
            DiseaseFollowupTask same = followupTaskMapper.selectOne(
                    new QueryWrapper<DiseaseFollowupTask>().eq("task_no", taskNo));
            if (same != null) {
                existed.add(same);
                continue;
            }
            DiseaseFollowupTask task = new DiseaseFollowupTask();
            task.setTaskNo(taskNo);
            task.setObservationId(observation.getId());
            task.setTaskDate(taskDate);
            task.setStatus(DiseaseFollowupTask.STATUS_PENDING);
            task.setOperator(DiseaseFollowupTask.SCHEDULER_OPERATOR);
            task.setSourceObserver(observation.getObserver());
            task.setRemark("请复核观察单" + observation.getObsNo() + "（风险等级"
                    + observation.getRiskLevel() + "）的疫病发展情况");
            followupTaskMapper.insert(task);
            created.add(task);
            evidenceService.record(DiseaseChangeEvidence.ENTITY_FOLLOWUP_TASK, task.getId(),
                    DiseaseChangeEvidence.ACTION_FOLLOWUP_GENERATE, taskNo,
                    DiseaseFollowupTask.SCHEDULER_OPERATOR, null, task,
                    "定时任务生成复查任务：观察单" + observation.getObsNo() + "，任务日" + taskDate
                            + "，原观察人=" + observation.getObserver());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskDate", taskDate);
        result.put("createdCount", created.size());
        result.put("existedCount", existed.size());
        result.put("created", created);
        result.put("existed", existed);
        return result;
    }

    public List<DiseaseFollowupTask> list(Long observationId, LocalDate taskDate) {
        QueryWrapper<DiseaseFollowupTask> wrapper = new QueryWrapper<>();
        wrapper.eq(observationId != null, "observation_id", observationId);
        wrapper.eq(taskDate != null, "task_date", taskDate);
        wrapper.orderByAsc("id");
        return followupTaskMapper.selectList(wrapper);
    }

    public static String taskNo(Long observationId, LocalDate taskDate) {
        return "FUP-" + observationId + "-" + taskDate.toString().replace("-", "");
    }
}
