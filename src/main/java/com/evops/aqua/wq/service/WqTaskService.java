package com.evops.aqua.wq.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.wq.dto.BackfillRequest;
import com.evops.aqua.wq.dto.TaskGenerateRequest;
import com.evops.aqua.wq.dto.TaskItemInput;
import com.evops.aqua.wq.dto.TaskReviewRequest;
import com.evops.aqua.wq.entity.WqBackfill;
import com.evops.aqua.wq.entity.WqChangeEvidence;
import com.evops.aqua.wq.entity.WqHealthPoint;
import com.evops.aqua.wq.entity.WqIndicatorRule;
import com.evops.aqua.wq.entity.WqPlan;
import com.evops.aqua.wq.entity.WqStageRule;
import com.evops.aqua.wq.entity.WqTask;
import com.evops.aqua.wq.entity.WqTaskItem;
import com.evops.aqua.wq.mapper.WqBackfillMapper;
import com.evops.aqua.wq.mapper.WqHealthPointMapper;
import com.evops.aqua.wq.mapper.WqTaskItemMapper;
import com.evops.aqua.wq.mapper.WqTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水质采样任务：计划按池生成；采样员暂存草稿/提交（缺测须说明原因）；
 * 补录只追加不覆盖；主管审核后才进入池塘健康曲线；所有修改保留证据。
 */
@Service
public class WqTaskService {

    private final WqTaskMapper taskMapper;
    private final WqTaskItemMapper itemMapper;
    private final WqBackfillMapper backfillMapper;
    private final WqHealthPointMapper healthPointMapper;
    private final WqPlanService planService;
    private final WqRuleVersionService ruleVersionService;
    private final WqEvidenceService evidenceService;

    public WqTaskService(WqTaskMapper taskMapper,
                         WqTaskItemMapper itemMapper,
                         WqBackfillMapper backfillMapper,
                         WqHealthPointMapper healthPointMapper,
                         WqPlanService planService,
                         WqRuleVersionService ruleVersionService,
                         WqEvidenceService evidenceService) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.backfillMapper = backfillMapper;
        this.healthPointMapper = healthPointMapper;
        this.planService = planService;
        this.ruleVersionService = ruleVersionService;
        this.evidenceService = evidenceService;
    }

    /**
     * 计划按池生成任务：按阶段采样频率在区间内逐池逐采样日生成。
     * 幂等：同计划同池同日只生成一次，重复请求返回已有任务，不产生第二条结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(TaskGenerateRequest request) {
        WqPlan plan = planService.getOrThrow(request.getPlanId());
        if (!WqPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("计划已取消，不能生成任务：" + plan.getPlanNo());
        }
        LocalDate start = request.getStartDate() == null ? plan.getStartDate() : request.getStartDate();
        LocalDate end = request.getEndDate() == null ? plan.getEndDate() : request.getEndDate();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("生成区间结束日期不能早于开始日期");
        }
        WqStageRule stage = ruleVersionService.getStageOrThrow(plan.getRuleVersionId(), plan.getStageCode());
        List<WqIndicatorRule> indicators = ruleVersionService.listIndicators(stage.getId());
        List<Long> pondIds = planService.listPondIds(plan.getId());
        if (pondIds.isEmpty()) {
            throw new IllegalArgumentException("计划未覆盖任何养殖池，无法生成任务");
        }

        List<WqTask> created = new ArrayList<>();
        List<WqTask> existed = new ArrayList<>();
        for (Long pondId : pondIds) {
            for (LocalDate date = start; !date.isAfter(end);
                 date = date.plusDays(stage.getSampleIntervalDays())) {
                List<WqTask> same = taskMapper.selectList(new QueryWrapper<WqTask>()
                        .eq("plan_id", plan.getId())
                        .eq("pond_id", pondId)
                        .eq("task_date", date));
                if (!same.isEmpty()) {
                    existed.add(same.get(0));
                    continue;
                }
                WqTask task = new WqTask();
                task.setTaskNo("WQ-" + plan.getId() + "-" + pondId + "-" + date);
                task.setPlanId(plan.getId());
                task.setPondId(pondId);
                task.setTaskDate(date);
                task.setStageCode(plan.getStageCode());
                task.setRuleVersionId(plan.getRuleVersionId());
                task.setStatus(WqTask.STATUS_DRAFT);
                taskMapper.insert(task);
                snapshotItems(task, indicators);
                created.add(task);
                evidenceService.record(WqChangeEvidence.ENTITY_TASK, task.getId(),
                        WqChangeEvidence.ACTION_GENERATE, request.getRequestId(), null, task,
                        "按计划" + plan.getPlanNo() + "为池塘id=" + pondId + "生成" + date + "采样任务");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createdCount", created.size());
        result.put("existedCount", existed.size());
        result.put("created", created);
        result.put("existed", existed);
        return result;
    }

    /**
     * 新版本规则只作用于未开始任务：
     * 1) 阶段在新版本中仍存在的在执行计划，版本指针切到新版本（后续生成即用新规则）；
     * 2) 这些计划下从未录入过任何指标的草稿任务，重绑版本并重建指标快照。
     * 已暂存/已提交/已审核任务一律不动。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebindUnstartedTasks(Long newVersionId) {
        List<WqStageRule> newStages = ruleVersionService.listStages(newVersionId);
        for (WqStageRule newStage : newStages) {
            List<WqIndicatorRule> newIndicators = ruleVersionService.listIndicators(newStage.getId());
            List<com.evops.aqua.wq.entity.WqPlan> plans = planMapperSelectActiveByStage(newStage.getStageCode());
            for (WqPlan plan : plans) {
                if (plan.getRuleVersionId().equals(newVersionId)) {
                    continue;
                }
                WqPlan before = copyOfPlan(plan);
                plan.setRuleVersionId(newVersionId);
                planService.updateRuleVersion(plan);
                evidenceService.record(WqChangeEvidence.ENTITY_PLAN, plan.getId(),
                        WqChangeEvidence.ACTION_UPDATE, null, before, plan,
                        "新规则版本" + newVersionId + "发布，未开始计划切换规则版本");

                List<WqTask> drafts = taskMapper.selectList(new QueryWrapper<WqTask>()
                        .eq("plan_id", plan.getId())
                        .eq("status", WqTask.STATUS_DRAFT));
                for (WqTask task : drafts) {
                    List<WqTaskItem> items = listItems(task.getId());
                    boolean started = items.stream().anyMatch(this::hasContent);
                    if (started) {
                        continue;
                    }
                    WqTask beforeTask = copyOfTask(task);
                    List<String> beforeCodes = new ArrayList<>();
                    for (WqTaskItem item : items) {
                        beforeCodes.add(item.getIndicatorCode());
                    }
                    // 重建空指标快照：旧行删除、按新版本插入
                    for (WqTaskItem item : items) {
                        itemMapper.deleteById(item.getId());
                    }
                    task.setRuleVersionId(newVersionId);
                    taskMapper.updateById(task);
                    snapshotItems(task, newIndicators);
                    evidenceService.record(WqChangeEvidence.ENTITY_TASK, task.getId(),
                            WqChangeEvidence.ACTION_UPDATE, null, beforeTask, task,
                            "未开始任务套用新规则版本，指标由" + beforeCodes
                                    + "变为" + indicatorCodes(newIndicators));
                }
            }
        }
    }

    /**
     * 采样员暂存草稿：可只录部分指标，缺测此时不强制原因。草稿/驳回态可反复暂存。
     */
    @Transactional(rollbackFor = Exception.class)
    public WqTask saveDraft(Long taskId, List<TaskItemInput> inputs) {
        WqTask before = copyOfTask(getOrThrow(taskId));
        WqTask task = getOrThrow(taskId);
        requireEditable(task, "暂存草稿");
        applyInputs(task, inputs);
        evidenceService.record(WqChangeEvidence.ENTITY_TASK, task.getId(),
                WqChangeEvidence.ACTION_UPDATE, null, before, task, "采样员暂存草稿");
        return task;
    }

    /**
     * 采样员提交：每个指标必须有值，缺测必须说明原因，否则拒绝。
     * 幂等：已提交/已审核任务重复提交直接返回原任务，不产生第二条结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public WqTask submit(Long taskId, List<TaskItemInput> inputs, String requestId) {
        WqTask task = getOrThrow(taskId);
        if (WqTask.STATUS_SUBMITTED.equals(task.getStatus())
                || WqTask.STATUS_APPROVED.equals(task.getStatus())) {
            return task;
        }
        if (!WqTask.STATUS_DRAFT.equals(task.getStatus()) && !WqTask.STATUS_REJECTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("当前任务状态不能提交：" + task.getStatus());
        }
        if (inputs != null) {
            applyInputs(task, inputs);
        }
        List<WqTaskItem> items = listItems(task.getId());
        for (WqTaskItem item : items) {
            boolean hasValue = item.getValueText() != null && !item.getValueText().trim().isEmpty();
            boolean hasReason = item.getMissingReason() != null && !item.getMissingReason().trim().isEmpty();
            if (!hasValue && !hasReason) {
                throw new IllegalArgumentException(
                        "指标[" + item.getIndicatorName() + "]缺测，提交时必须说明缺测原因");
            }
            if (hasValue) {
                requireNumeric(item);
            }
        }
        WqTask before = copyOfTask(task);
        task.setStatus(WqTask.STATUS_SUBMITTED);
        task.setSubmittedTime(LocalDateTime.now());
        taskMapper.updateById(task);
        evidenceService.record(WqChangeEvidence.ENTITY_TASK, task.getId(),
                WqChangeEvidence.ACTION_SUBMIT, requestId, before, task, "采样员提交采样结果");
        return task;
    }

    /**
     * 主管审核。通过：已测数值进入池塘健康曲线（缺测项不入曲线）；驳回：退回并填写意见。
     * 幂等：已通过任务重复通过直接返回；已入曲线点不重复写入。
     */
    @Transactional(rollbackFor = Exception.class)
    public WqTask review(Long taskId, TaskReviewRequest request) {
        WqTask task = getOrThrow(taskId);
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        if (approved && WqTask.STATUS_APPROVED.equals(task.getStatus())) {
            return task;
        }
        if (!WqTask.STATUS_SUBMITTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("只有已提交任务才能审核，当前状态：" + task.getStatus());
        }
        WqTask before = copyOfTask(task);
        task.setReviewedTime(LocalDateTime.now());
        task.setReviewRemark(request.getRemark());
        if (approved) {
            task.setStatus(WqTask.STATUS_APPROVED);
            taskMapper.updateById(task);
            publishHealthPoints(task);
            evidenceService.record(WqChangeEvidence.ENTITY_TASK, task.getId(),
                    WqChangeEvidence.ACTION_APPROVE, request.getRequestId(), before, task,
                    "主管审核通过，采样值进入池塘健康曲线");
        } else {
            if (request.getRemark() == null || request.getRemark().trim().isEmpty()) {
                throw new IllegalArgumentException("驳回必须填写审核意见");
            }
            task.setStatus(WqTask.STATUS_REJECTED);
            taskMapper.updateById(task);
            evidenceService.record(WqChangeEvidence.ENTITY_TASK, task.getId(),
                    WqChangeEvidence.ACTION_REJECT, request.getRequestId(), before, task,
                    "主管审核驳回：" + request.getRemark());
        }
        return task;
    }

    /**
     * 缺测/历史补录：只追加一行，绝不覆盖原测量项与已入曲线的值。
     * 幂等：相同 requestId 重复请求返回首次补录，不产生第二条结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public WqBackfill backfill(BackfillRequest request) {
        List<WqBackfill> sameRequest = backfillMapper.selectList(
                new QueryWrapper<WqBackfill>().eq("request_id", request.getRequestId()));
        if (!sameRequest.isEmpty()) {
            return sameRequest.get(0);
        }
        WqTask task = getOrThrow(request.getTaskId());
        WqTaskItem item = findItem(task.getId(), request.getIndicatorCode());
        boolean hasValue = request.getValueText() != null && !request.getValueText().trim().isEmpty();
        boolean hasReason = request.getMissingReason() != null && !request.getMissingReason().trim().isEmpty();
        if (hasValue == hasReason) {
            throw new IllegalArgumentException("补录必须且只能提供已测数值或缺测原因之一");
        }
        if (hasValue) {
            new BigDecimal(request.getValueText().trim());
        }

        WqBackfill backfill = new WqBackfill();
        backfill.setTaskItemId(item.getId());
        backfill.setTaskId(task.getId());
        backfill.setRequestId(request.getRequestId());
        backfill.setIndicatorCode(item.getIndicatorCode());
        backfill.setValueText(hasValue ? request.getValueText().trim() : null);
        backfill.setMissingReason(hasReason ? request.getMissingReason().trim() : null);
        backfill.setBackfillDate(request.getBackfillDate());
        backfill.setEntryType(hasValue ? WqBackfill.ENTRY_MEASURED : WqBackfill.ENTRY_MISSING);
        backfillMapper.insert(backfill);
        // 注意：刻意不更新 t_wq_task_item.value_text —— 补录只能追加，不能覆盖原值。

        WqTaskItem afterItem = itemMapper.selectById(item.getId());
        evidenceService.record(WqChangeEvidence.ENTITY_BACKFILL, backfill.getId(),
                WqChangeEvidence.ACTION_BACKFILL, request.getRequestId(), null, backfill,
                "补录追加（原测量项保持不变）：" + item.getIndicatorCode()
                        + "，原值=" + item.getValueText());
        evidenceService.record(WqChangeEvidence.ENTITY_TASK_ITEM, item.getId(),
                WqChangeEvidence.ACTION_UPDATE, request.getRequestId(), item, afterItem,
                "补录不覆盖原值，原测量项快照保持：" + item.getValueText());
        return backfill;
    }

    public WqTask getOrThrow(Long id) {
        WqTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("采样任务不存在，id=" + id);
        }
        return task;
    }

    public List<WqTask> listTasks(Long pondId, Long planId, String status) {
        QueryWrapper<WqTask> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(planId != null, "plan_id", planId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("task_date", "id");
        return taskMapper.selectList(wrapper);
    }

    public Map<String, Object> taskDetail(Long taskId) {
        WqTask task = getOrThrow(taskId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", task);
        detail.put("items", listItems(taskId));
        detail.put("backfills", backfillMapper.selectList(
                new QueryWrapper<WqBackfill>().eq("task_id", taskId).orderByAsc("id")));
        return detail;
    }

    /**
     * 池塘健康曲线：仅含主管审核通过后写入的点，按指标与采样日期排序。
     */
    public List<WqHealthPoint> healthCurve(Long pondId, String indicatorCode) {
        QueryWrapper<WqHealthPoint> wrapper = new QueryWrapper<>();
        wrapper.eq("pond_id", pondId);
        wrapper.eq(indicatorCode != null && !indicatorCode.trim().isEmpty(),
                "indicator_code", indicatorCode);
        wrapper.orderByAsc("indicator_code", "sample_date", "id");
        return healthPointMapper.selectList(wrapper);
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private void snapshotItems(WqTask task, List<WqIndicatorRule> indicators) {
        for (WqIndicatorRule rule : indicators) {
            WqTaskItem item = new WqTaskItem();
            item.setTaskId(task.getId());
            item.setIndicatorCode(rule.getIndicatorCode());
            item.setIndicatorName(rule.getIndicatorName());
            item.setUnit(rule.getUnit());
            item.setNormalMin(rule.getMinValue());
            item.setNormalMax(rule.getMaxValue());
            item.setRequiredFlag(rule.getRequiredFlag());
            item.setMissingFlag(Boolean.FALSE);
            itemMapper.insert(item);
        }
    }

    private void publishHealthPoints(WqTask task) {
        for (WqTaskItem item : listItems(task.getId())) {
            if (item.getValueText() == null || item.getValueText().trim().isEmpty()) {
                continue; // 缺测项不进入健康曲线
            }
            Long exists = healthPointMapper.selectCount(new QueryWrapper<WqHealthPoint>()
                    .eq("task_id", task.getId()).eq("indicator_code", item.getIndicatorCode()));
            if (exists != null && exists > 0) {
                continue;
            }
            WqHealthPoint point = new WqHealthPoint();
            point.setPondId(task.getPondId());
            point.setTaskId(task.getId());
            point.setIndicatorCode(item.getIndicatorCode());
            point.setIndicatorName(item.getIndicatorName());
            point.setUnit(item.getUnit());
            point.setSampleDate(task.getTaskDate());
            point.setSampleValue(new BigDecimal(item.getValueText().trim()));
            point.setTaskRuleVersionId(task.getRuleVersionId());
            healthPointMapper.insert(point);
            evidenceService.record(WqChangeEvidence.ENTITY_HEALTH_POINT, point.getId(),
                    WqChangeEvidence.ACTION_CREATE, null, null, point,
                    "主管审核通过，指标[" + item.getIndicatorCode() + "]进入池塘健康曲线");
        }
    }

    private void applyInputs(WqTask task, List<TaskItemInput> inputs) {
        for (TaskItemInput input : inputs) {
            WqTaskItem item = findItem(task.getId(), input.getIndicatorCode());
            boolean missing = Boolean.TRUE.equals(input.getMissingFlag());
            if (missing) {
                item.setValueText(null);
                item.setMissingFlag(Boolean.TRUE);
                item.setMissingReason(input.getMissingReason());
            } else {
                if (input.getValueText() != null && !input.getValueText().trim().isEmpty()) {
                    String value = input.getValueText().trim();
                    new BigDecimal(value); // 非数字直接拒绝
                    item.setValueText(value);
                    item.setMissingFlag(Boolean.FALSE);
                    item.setMissingReason(null);
                }
            }
            itemMapper.updateById(item);
        }
    }

    private void requireEditable(WqTask task, String action) {
        if (!WqTask.STATUS_DRAFT.equals(task.getStatus()) && !WqTask.STATUS_REJECTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("任务已提交或已审核，不能" + action + "，状态=" + task.getStatus());
        }
    }

    private void requireNumeric(WqTaskItem item) {
        try {
            new BigDecimal(item.getValueText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "指标[" + item.getIndicatorName() + "]的采样值不是数值：" + item.getValueText());
        }
    }

    private boolean hasContent(WqTaskItem item) {
        return (item.getValueText() != null && !item.getValueText().trim().isEmpty())
                || (item.getMissingReason() != null && !item.getMissingReason().trim().isEmpty());
    }

    private WqTaskItem findItem(Long taskId, String indicatorCode) {
        List<WqTaskItem> items = itemMapper.selectList(new QueryWrapper<WqTaskItem>()
                .eq("task_id", taskId).eq("indicator_code", indicatorCode));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("任务下无该指标：" + indicatorCode);
        }
        return items.get(0);
    }

    private List<WqTaskItem> listItems(Long taskId) {
        return itemMapper.selectList(
                new QueryWrapper<WqTaskItem>().eq("task_id", taskId).orderByAsc("id"));
    }

    private List<WqPlan> planMapperSelectActiveByStage(String stageCode) {
        return planService.listByStageAndStatus(stageCode, WqPlan.STATUS_ACTIVE);
    }

    private List<String> indicatorCodes(List<WqIndicatorRule> indicators) {
        List<String> codes = new ArrayList<>();
        for (WqIndicatorRule rule : indicators) {
            codes.add(rule.getIndicatorCode());
        }
        return codes;
    }

    private WqTask copyOfTask(WqTask source) {
        WqTask copy = new WqTask();
        copy.setId(source.getId());
        copy.setTaskNo(source.getTaskNo());
        copy.setPlanId(source.getPlanId());
        copy.setPondId(source.getPondId());
        copy.setTaskDate(source.getTaskDate());
        copy.setStageCode(source.getStageCode());
        copy.setRuleVersionId(source.getRuleVersionId());
        copy.setStatus(source.getStatus());
        copy.setSubmittedBy(source.getSubmittedBy());
        copy.setSubmittedTime(source.getSubmittedTime());
        copy.setReviewedBy(source.getReviewedBy());
        copy.setReviewedTime(source.getReviewedTime());
        copy.setReviewRemark(source.getReviewRemark());
        return copy;
    }

    private WqPlan copyOfPlan(WqPlan source) {
        WqPlan copy = new WqPlan();
        copy.setId(source.getId());
        copy.setPlanNo(source.getPlanNo());
        copy.setPlanName(source.getPlanName());
        copy.setRuleVersionId(source.getRuleVersionId());
        copy.setStageCode(source.getStageCode());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
