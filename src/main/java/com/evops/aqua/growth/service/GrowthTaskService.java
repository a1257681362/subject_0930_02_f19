package com.evops.aqua.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.growth.dto.SampleInput;
import com.evops.aqua.growth.dto.SampleRecordRequest;
import com.evops.aqua.growth.dto.SampleReviewRequest;
import com.evops.aqua.growth.dto.TaskConfirmRequest;
import com.evops.aqua.growth.dto.GrowthTaskGenerateRequest;
import com.evops.aqua.growth.dto.GrowthTaskSubmitRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceCoefficient;
import com.evops.aqua.growth.entity.GrowthHarvestForecast;
import com.evops.aqua.growth.entity.GrowthMeasurementSample;
import com.evops.aqua.growth.entity.GrowthMeasurementTask;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.mapper.GrowthHarvestForecastMapper;
import com.evops.aqua.growth.mapper.GrowthMeasurementSampleMapper;
import com.evops.aqua.growth.mapper.GrowthMeasurementTaskMapper;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生长测量任务：按方案生成（冻结抽样数/偏差/设备系数版本）；现场录入体重长度后按冻结系数校正求均值；
 * 异常样本只能标记并填复核理由，绝不删除；提交时做允许偏差校验；主管确认后才写预测出池量；
 * 历史任务不随新系数重算；支持从测量记录反查抽样方案（原单）、处理人及异常原因。
 */
@Service
public class GrowthTaskService {

    private static final int SCALE = 3;
    private static final int PCT_SCALE = 2;

    private final GrowthMeasurementTaskMapper taskMapper;
    private final GrowthMeasurementSampleMapper sampleMapper;
    private final GrowthHarvestForecastMapper forecastMapper;
    private final GrowthPlanService planService;
    private final GrowthCoefficientService coefficientService;
    private final SeedlingBatchService batchService;
    private final GrowthEvidenceService evidenceService;

    public GrowthTaskService(GrowthMeasurementTaskMapper taskMapper,
                             GrowthMeasurementSampleMapper sampleMapper,
                             GrowthHarvestForecastMapper forecastMapper,
                             GrowthPlanService planService,
                             GrowthCoefficientService coefficientService,
                             SeedlingBatchService batchService,
                             GrowthEvidenceService evidenceService) {
        this.taskMapper = taskMapper;
        this.sampleMapper = sampleMapper;
        this.forecastMapper = forecastMapper;
        this.planService = planService;
        this.coefficientService = coefficientService;
        this.batchService = batchService;
        this.evidenceService = evidenceService;
    }

    // ------------------------------------------------------------
    // 任务生成
    // ------------------------------------------------------------

    /**
     * 按方案在区间内按测量频次逐测量日生成任务。冻结抽样数、允许偏差、设备与系数版本。
     * 幂等：同方案同池同批同日只一条，重复请求识别为已存在，不产生第二条。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(Long planId, GrowthTaskGenerateRequest request, String operator) {
        GrowthSamplingPlan plan = planService.getOrThrow(planId);
        if (!GrowthSamplingPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("抽样方案已取消，不能生成测量任务：" + plan.getPlanNo());
        }
        LocalDate start = request.getStartDate() == null ? plan.getStartDate() : request.getStartDate();
        LocalDate end = request.getEndDate() == null ? plan.getEndDate() : request.getEndDate();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("生成区间结束日期不能早于开始日期");
        }
        GrowthDeviceCoefficient coeff = coefficientService.getOrThrow(plan.getCoeffVersionId());

        List<GrowthMeasurementTask> created = new ArrayList<>();
        List<GrowthMeasurementTask> existed = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end);
             date = date.plusDays(plan.getMeasureIntervalDays())) {
            List<GrowthMeasurementTask> same = taskMapper.selectList(new QueryWrapper<GrowthMeasurementTask>()
                    .eq("plan_id", plan.getId())
                    .eq("pond_id", plan.getPondId())
                    .eq("batch_id", plan.getBatchId())
                    .eq("measure_date", date));
            if (!same.isEmpty()) {
                existed.add(same.get(0));
                continue;
            }
            GrowthMeasurementTask task = new GrowthMeasurementTask();
            task.setTaskNo("GM-" + plan.getId() + "-" + plan.getPondId() + "-" + date);
            task.setPlanId(plan.getId());
            task.setPondId(plan.getPondId());
            task.setBatchId(plan.getBatchId());
            task.setMeasureDate(date);
            task.setSampleCount(plan.getSampleCount());
            task.setAllowedDeviationPct(plan.getAllowedDeviationPct());
            task.setDeviceCode(plan.getDeviceCode());
            task.setCoeffVersionId(coeff.getId());
            task.setStatus(GrowthMeasurementTask.STATUS_DRAFT);
            taskMapper.insert(task);
            created.add(task);
            evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                    GrowthChangeEvidence.ACTION_GENERATE, request.getRequestId(), operator, null, task,
                    "按抽样方案" + plan.getPlanNo() + "为池塘id=" + plan.getPondId()
                            + "批次id=" + plan.getBatchId() + "生成" + date + "测量任务"
                            + task.getTaskNo() + "，抽样数=" + plan.getSampleCount()
                            + "，冻结设备=" + coeff.getDeviceCode() + "系数版本id=" + coeff.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createdCount", created.size());
        result.put("existedCount", existed.size());
        result.put("created", created);
        result.put("existed", existed);
        return result;
    }

    // ------------------------------------------------------------
    // 现场录入与异常标记
    // ------------------------------------------------------------

    /**
     * 现场录入样本：逐序号 upsert。校正值用任务冻结的设备系数计算，随后按未标记异常样本重算均值。
     * 已被标记异常的样本行受保护，不能被重新录入覆盖（只能走复核接口）；任何样本行都不会被删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasurementTask recordSamples(Long taskId, SampleRecordRequest request) {
        GrowthMeasurementTask before = copyOf(getOrThrow(taskId));
        GrowthMeasurementTask task = getOrThrow(taskId);
        requireEditable(task, "录入测量数据");

        GrowthDeviceCoefficient coeff = coefficientService.getOrThrow(task.getCoeffVersionId());

        for (SampleInput input : request.getSamples()) {
            GrowthMeasurementSample existing = findSampleNullable(taskId, input.getSeqNo());
            if (existing != null && Boolean.TRUE.equals(existing.getAbnormalityFlag())) {
                throw new IllegalArgumentException("第" + input.getSeqNo()
                        + "尾样本已标记异常（复核理由：" + existing.getReviewReason()
                        + "），不能被重新录入覆盖；如需改判请走异常复核");
            }

            boolean abnormal = Boolean.TRUE.equals(input.getAbnormalityFlag());
            if (abnormal && (input.getReviewReason() == null || input.getReviewReason().trim().isEmpty())) {
                throw new IllegalArgumentException("第" + input.getSeqNo()
                        + "尾样本标记异常时必须填写复核理由，异常样本只能标记不能删除");
            }

            BigDecimal correctedWeight = input.getRawWeightG().multiply(coeff.getWeightCoeff())
                    .setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal correctedLength = input.getRawLengthMm().multiply(coeff.getLengthCoeff())
                    .setScale(SCALE, RoundingMode.HALF_UP);

            if (existing == null) {
                GrowthMeasurementSample sample = new GrowthMeasurementSample();
                sample.setTaskId(taskId);
                sample.setSeqNo(input.getSeqNo());
                applyReading(sample, input, coeff, correctedWeight, correctedLength, abnormal);
                sample.setIncludedFlag(!abnormal);
                sampleMapper.insert(sample);
                evidenceService.record(GrowthChangeEvidence.ENTITY_SAMPLE, sample.getId(),
                        GrowthChangeEvidence.ACTION_RECORD, request.getRequestId(),
                        request.getMeasuredBy(), null, sample,
                        "现场录入第" + sample.getSeqNo() + "尾：原始体重=" + input.getRawWeightG()
                                + "g/体长=" + input.getRawLengthMm() + "mm，按设备"
                                + coeff.getDeviceCode() + "系数版本" + coeff.getVersionNo()
                                + "校正为体重=" + correctedWeight + "g/体长=" + correctedLength + "mm"
                                + (abnormal ? "，录入即标记异常：" + input.getReviewReason() : ""));
            } else {
                GrowthMeasurementSample sampleBefore = copyOfSample(existing);
                applyReading(existing, input, coeff, correctedWeight, correctedLength, abnormal);
                existing.setIncludedFlag(!abnormal);
                if (abnormal) {
                    existing.setReviewedBy(request.getMeasuredBy());
                    existing.setReviewedTime(LocalDateTime.now());
                }
                sampleMapper.updateById(existing);
                evidenceService.record(GrowthChangeEvidence.ENTITY_SAMPLE, existing.getId(),
                        GrowthChangeEvidence.ACTION_UPDATE, request.getRequestId(),
                        request.getMeasuredBy(), sampleBefore, existing,
                        "重新录入第" + existing.getSeqNo() + "尾读数并按冻结系数版本"
                                + coeff.getVersionNo() + "重新校正（样本行保留不删除）");
            }
        }

        task.setMeasuredBy(request.getMeasuredBy());
        task.setTotalCount(request.getTotalCount());
        if (request.getRemark() != null) {
            task.setRemark(request.getRemark());
        }
        recomputeAverages(task);
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_UPDATE, request.getRequestId(),
                request.getMeasuredBy(), before, task,
                "现场录入测量数据，处理人=" + request.getMeasuredBy()
                        + "，按未标记异常样本重算均值");
        return task;
    }

    /**
     * 异常复核：对已录入样本标记/取消异常，必须填复核理由与复核人。
     * 只改异常标记与理由，绝不删除样本；改判后按纳入样本重算均值。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasurementSample reviewSample(Long taskId, Integer seqNo, SampleReviewRequest request) {
        GrowthMeasurementTask task = getOrThrow(taskId);
        requireEditable(task, "异常复核");
        GrowthMeasurementSample before = copyOfSample(findSample(taskId, seqNo));
        GrowthMeasurementSample sample = findSample(taskId, seqNo);

        boolean abnormal = Boolean.TRUE.equals(request.getAbnormalityFlag());
        if (abnormal && (request.getReviewReason() == null || request.getReviewReason().trim().isEmpty())) {
            throw new IllegalArgumentException("标记异常必须填写复核理由，异常样本只能标记不能静默删除");
        }
        sample.setAbnormalityFlag(abnormal);
        sample.setIncludedFlag(!abnormal);
        sample.setReviewedBy(request.getReviewedBy());
        sample.setReviewedTime(LocalDateTime.now());
        if (abnormal) {
            sample.setReviewReason(request.getReviewReason().trim());
        } else {
            sample.setReviewReason(null);
        }
        sampleMapper.updateById(sample);

        recomputeAverages(task);
        taskMapper.updateById(task);

        evidenceService.record(GrowthChangeEvidence.ENTITY_SAMPLE, sample.getId(),
                GrowthChangeEvidence.ACTION_FLAG, request.getRequestId(),
                request.getReviewedBy(), before, sample,
                abnormal
                        ? "第" + sample.getSeqNo() + "尾标记异常并剔除均值，复核理由：" + sample.getReviewReason()
                        : "第" + sample.getSeqNo() + "尾复核改判正常，重新计入均值（样本行始终保留）");
        return sample;
    }

    // ------------------------------------------------------------
    // 提交与主管确认
    // ------------------------------------------------------------

    /**
     * 测量员提交：校验样本齐全、异常均有理由、纳入样本相对均值不超允许偏差。
     * 幂等：已提交/已确认任务重复提交直接返回原任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasurementTask submit(Long taskId, GrowthTaskSubmitRequest request) {
        GrowthMeasurementTask task = getOrThrow(taskId);
        if (GrowthMeasurementTask.STATUS_SUBMITTED.equals(task.getStatus())
                || GrowthMeasurementTask.STATUS_CONFIRMED.equals(task.getStatus())) {
            return task;
        }
        if (!GrowthMeasurementTask.STATUS_DRAFT.equals(task.getStatus())
                && !GrowthMeasurementTask.STATUS_REJECTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("当前任务状态不能提交：" + task.getStatus());
        }
        List<GrowthMeasurementSample> samples = listSamples(taskId);
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("尚未录入任何样本，不能提交");
        }
        if (samples.size() < task.getSampleCount()) {
            throw new IllegalArgumentException("实际抽样" + samples.size() + "尾少于方案配置抽样数量"
                    + task.getSampleCount() + "尾，请按抽样方案补齐");
        }
        for (GrowthMeasurementSample sample : samples) {
            if (Boolean.TRUE.equals(sample.getAbnormalityFlag())
                    && (sample.getReviewReason() == null || sample.getReviewReason().trim().isEmpty())) {
                throw new IllegalArgumentException("第" + sample.getSeqNo()
                        + "尾为异常样本但未填写复核理由，不能提交");
            }
        }
        List<GrowthMeasurementSample> included = includedSamples(samples);
        if (included.isEmpty()) {
            throw new IllegalArgumentException("样本全部被标记异常，没有可用于计算均值的有效样本");
        }
        // 以纳入均值样本的校正体重做允许偏差校验：超差样本须先标记异常并填理由
        BigDecimal avgWeight = average(included, true);
        for (GrowthMeasurementSample sample : included) {
            BigDecimal deviationPct = deviationPercent(sample.getCorrectedWeightG(), avgWeight);
            if (deviationPct.compareTo(task.getAllowedDeviationPct()) > 0) {
                throw new IllegalArgumentException("第" + sample.getSeqNo() + "尾校正体重"
                        + sample.getCorrectedWeightG() + "g偏离均值" + avgWeight + "g达"
                        + deviationPct + "%，超过允许偏差" + task.getAllowedDeviationPct()
                        + "%；如确属异常请标记并填写复核理由，复核正常后方可提交");
            }
        }

        GrowthMeasurementTask before = copyOf(task);
        task.setStatus(GrowthMeasurementTask.STATUS_SUBMITTED);
        task.setSubmittedBy(task.getMeasuredBy());
        task.setSubmittedTime(LocalDateTime.now());
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_SUBMIT, request.getRequestId(),
                task.getMeasuredBy(), before, task,
                "测量员提交，处理人=" + task.getMeasuredBy() + "，有效样本=" + included.size()
                        + "尾，异常样本=" + (samples.size() - included.size()) + "尾");
        return task;
    }

    /**
     * 主管确认/驳回。通过：写该任务唯一一条预测出池量（预测口径冻结在任务当时，历史不随新系数重算）；
     * 驳回：必须填意见，退回重录重提。幂等：已确认任务重复确认直接返回，不重复写预测。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasurementTask confirm(Long taskId, TaskConfirmRequest request) {
        GrowthMeasurementTask task = getOrThrow(taskId);
        boolean approved = !Boolean.FALSE.equals(request.getApproved());
        if (approved && GrowthMeasurementTask.STATUS_CONFIRMED.equals(task.getStatus())) {
            return task;
        }
        if (!GrowthMeasurementTask.STATUS_SUBMITTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("只有已提交任务才能主管确认，当前状态：" + task.getStatus());
        }
        GrowthMeasurementTask before = copyOf(task);

        if (!approved) {
            if (request.getRemark() == null || request.getRemark().trim().isEmpty()) {
                throw new IllegalArgumentException("驳回必须填写意见");
            }
            task.setStatus(GrowthMeasurementTask.STATUS_REJECTED);
            task.setRejectRemark(request.getRemark().trim());
            taskMapper.updateById(task);
            evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                    GrowthChangeEvidence.ACTION_REJECT, request.getRequestId(),
                    request.getConfirmedBy(), before, task,
                    "主管驳回：" + request.getRemark() + "（不产生预测出池量）");
            return task;
        }

        SeedlingBatch batch = batchService.getOrThrow(task.getBatchId());
        List<GrowthMeasurementSample> included = includedSamples(listSamples(taskId));
        if (included.isEmpty()) {
            throw new IllegalArgumentException("没有有效样本，无法确认预测出池量");
        }
        BigDecimal avgWeight = average(included, true);
        BigDecimal avgLength = average(included, false);
        int totalCount = task.getTotalCount() == null ? batch.getQuantity() : task.getTotalCount();
        // 预测出池量(kg) = 校正平均体重(g) × 在池总尾数 / 1000
        BigDecimal predictedKg = avgWeight.multiply(BigDecimal.valueOf(totalCount))
                .divide(BigDecimal.valueOf(1000), SCALE, RoundingMode.HALF_UP);
        BigDecimal survivalRatePct = BigDecimal.valueOf(totalCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(batch.getQuantity()), PCT_SCALE, RoundingMode.HALF_UP);

        GrowthHarvestForecast forecast = upsertForecast(task, batch, avgWeight, avgLength,
                totalCount, predictedKg, survivalRatePct, request);

        task.setStatus(GrowthMeasurementTask.STATUS_CONFIRMED);
        task.setConfirmedBy(request.getConfirmedBy());
        task.setConfirmedTime(LocalDateTime.now());
        task.setConfirmRemark(request.getRemark());
        task.setPredictedHarvestKg(predictedKg);
        task.setPredictedHarvestDate(batch.getPlannedHarvestDate());
        task.setTotalCount(totalCount);
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_CONFIRM, request.getRequestId(),
                request.getConfirmedBy(), before, task,
                "主管确认，预测出池量=" + predictedKg + "kg（均重=" + avgWeight + "g×在池"
                        + totalCount + "尾/1000），系数版本id=" + task.getCoeffVersionId()
                        + "；预测只在本次确认落定，历史测量不随新系数重算");
        evidenceService.record(GrowthChangeEvidence.ENTITY_FORECAST, forecast.getId(),
                GrowthChangeEvidence.ACTION_CREATE, request.getRequestId(),
                request.getConfirmedBy(), null, forecast,
                "写入预测出池量结果，关联测量任务=" + task.getTaskNo());
        return task;
    }

    // ------------------------------------------------------------
    // 查询与反查
    // ------------------------------------------------------------

    public GrowthMeasurementTask getOrThrow(Long id) {
        GrowthMeasurementTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("生长测量任务不存在，id=" + id);
        }
        return task;
    }

    public List<GrowthMeasurementTask> listTasks(Long pondId, Long batchId, Long planId, String status) {
        QueryWrapper<GrowthMeasurementTask> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(planId != null, "plan_id", planId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("measure_date", "id");
        return taskMapper.selectList(wrapper);
    }

    /**
     * 测量任务明细：任务 + 原抽样方案（原单）+ 冻结系数版本 + 全部样本（含异常标记/复核理由/复核人）
     * + 处理人链 + 预测出池量。供从测量记录反查抽样方案、处理人及异常原因。
     */
    public Map<String, Object> taskDetail(Long taskId) {
        GrowthMeasurementTask task = getOrThrow(taskId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", task);
        detail.put("plan", planService.getOrThrow(task.getPlanId()));
        detail.put("coefficient", coefficientService.getOrThrow(task.getCoeffVersionId()));
        detail.put("samples", listSamples(taskId));
        detail.put("forecast", forecastMapper.selectOne(
                new QueryWrapper<GrowthHarvestForecast>().eq("task_id", taskId)));
        detail.put("handlers", handlersOf(task));
        return detail;
    }

    /**
     * 从海水养殖管理侧的测量记录反查抽样方案：
     * 按池塘 + 批次（可加测量日、任务号）定位原单（plan_no/task_no）、处理人及异常原因。
     */
    public Map<String, Object> reverseTrace(Long pondId, Long batchId, LocalDate measureDate, String taskNo) {
        QueryWrapper<GrowthMeasurementTask> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(measureDate != null, "measure_date", measureDate);
        wrapper.eq(taskNo != null && !taskNo.trim().isEmpty(), "task_no", taskNo);
        wrapper.orderByAsc("measure_date", "id");
        List<GrowthMeasurementTask> tasks = taskMapper.selectList(wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (GrowthMeasurementTask task : tasks) {
            GrowthSamplingPlan plan = planService.getOrThrow(task.getPlanId());
            List<Map<String, Object>> abnormal = new ArrayList<>();
            for (GrowthMeasurementSample sample : listSamples(task.getId())) {
                if (Boolean.TRUE.equals(sample.getAbnormalityFlag())) {
                    Map<String, Object> ab = new LinkedHashMap<>();
                    ab.put("seqNo", sample.getSeqNo());
                    ab.put("rawWeightG", sample.getRawWeightG());
                    ab.put("rawLengthMm", sample.getRawLengthMm());
                    ab.put("correctedWeightG", sample.getCorrectedWeightG());
                    ab.put("reviewReason", sample.getReviewReason());
                    ab.put("reviewedBy", sample.getReviewedBy());
                    ab.put("reviewedTime", sample.getReviewedTime());
                    abnormal.add(ab);
                }
            }
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("sourcePlan", planNoOf(plan));          // 原抽样方案单号
            rec.put("taskNo", task.getTaskNo());            // 原测量单号
            rec.put("measureDate", task.getMeasureDate());
            rec.put("handlers", handlersOf(task));          // 处理人
            rec.put("abnormalSamples", abnormal);           // 异常原因
            rec.put("taskId", task.getId());
            rec.put("planId", plan.getId());
            records.add(rec);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pondId", pondId);
        result.put("batchId", batchId);
        result.put("measureDate", measureDate);
        result.put("count", records.size());
        result.put("records", records);
        return result;
    }

    public List<GrowthHarvestForecast> listForecasts(Long pondId, Long batchId) {
        QueryWrapper<GrowthHarvestForecast> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByAsc("measure_date", "id");
        return forecastMapper.selectList(wrapper);
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private GrowthHarvestForecast upsertForecast(GrowthMeasurementTask task, SeedlingBatch batch,
                                                 BigDecimal avgWeight, BigDecimal avgLength,
                                                 int totalCount, BigDecimal predictedKg,
                                                 BigDecimal survivalRatePct,
                                                 TaskConfirmRequest request) {
        GrowthHarvestForecast forecast = forecastMapper.selectOne(
                new QueryWrapper<GrowthHarvestForecast>().eq("task_id", task.getId()));
        boolean isNew = forecast == null;
        if (isNew) {
            forecast = new GrowthHarvestForecast();
            forecast.setTaskId(task.getId());
        }
        forecast.setPondId(task.getPondId());
        forecast.setBatchId(task.getBatchId());
        forecast.setMeasureDate(task.getMeasureDate());
        forecast.setAvgWeightG(avgWeight);
        forecast.setAvgLengthMm(avgLength);
        forecast.setSurvivalRatePct(survivalRatePct);
        forecast.setTotalCount(totalCount);
        forecast.setPredictedHarvestKg(predictedKg);
        forecast.setPredictedHarvestDate(batch.getPlannedHarvestDate());
        forecast.setCoeffVersionId(task.getCoeffVersionId());
        forecast.setConfirmedBy(request.getConfirmedBy());
        forecast.setConfirmedTime(LocalDateTime.now());
        forecast.setConfirmRemark(request.getRemark());
        forecast.setRequestId(request.getRequestId());
        if (isNew) {
            forecastMapper.insert(forecast);
        } else {
            forecastMapper.updateById(forecast);
        }
        return forecast;
    }

    private void applyReading(GrowthMeasurementSample sample, SampleInput input,
                              GrowthDeviceCoefficient coeff,
                              BigDecimal correctedWeight, BigDecimal correctedLength, boolean abnormal) {
        sample.setRawWeightG(input.getRawWeightG());
        sample.setRawLengthMm(input.getRawLengthMm());
        sample.setWeightCoeff(coeff.getWeightCoeff());
        sample.setLengthCoeff(coeff.getLengthCoeff());
        sample.setCoeffVersionId(coeff.getId());
        sample.setCorrectedWeightG(correctedWeight);
        sample.setCorrectedLengthMm(correctedLength);
        sample.setAbnormalityFlag(abnormal);
        sample.setReviewReason(abnormal ? input.getReviewReason().trim() : null);
    }

    /** 按未标记异常（included）样本重算原始与校正均值；无有效样本时均值置空。 */
    private void recomputeAverages(GrowthMeasurementTask task) {
        List<GrowthMeasurementSample> included = includedSamples(listSamples(task.getId()));
        if (included.isEmpty()) {
            task.setRawAvgWeightG(null);
            task.setRawAvgLengthMm(null);
            task.setCorrectedAvgWeightG(null);
            task.setCorrectedAvgLengthMm(null);
            return;
        }
        task.setRawAvgWeightG(average(included, true, false));
        task.setRawAvgLengthMm(average(included, false, false));
        task.setCorrectedAvgWeightG(average(included, true, true));
        task.setCorrectedAvgLengthMm(average(included, false, true));
    }

    /** 纳入样本的校正均值（体重/长度）。 */
    private BigDecimal average(List<GrowthMeasurementSample> samples, boolean weight) {
        return average(samples, weight, true);
    }

    private BigDecimal average(List<GrowthMeasurementSample> samples, boolean weight, boolean corrected) {
        BigDecimal sum = BigDecimal.ZERO;
        for (GrowthMeasurementSample s : samples) {
            BigDecimal value;
            if (weight) {
                value = corrected ? s.getCorrectedWeightG() : s.getRawWeightG();
            } else {
                value = corrected ? s.getCorrectedLengthMm() : s.getRawLengthMm();
            }
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(samples.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal deviationPercent(BigDecimal value, BigDecimal avg) {
        if (avg.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.subtract(avg).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(avg, PCT_SCALE, RoundingMode.HALF_UP);
    }

    private List<GrowthMeasurementSample> includedSamples(List<GrowthMeasurementSample> samples) {
        List<GrowthMeasurementSample> included = new ArrayList<>();
        for (GrowthMeasurementSample sample : samples) {
            if (!Boolean.TRUE.equals(sample.getAbnormalityFlag())) {
                included.add(sample);
            }
        }
        return included;
    }

    private void requireEditable(GrowthMeasurementTask task, String action) {
        if (!GrowthMeasurementTask.STATUS_DRAFT.equals(task.getStatus())
                && !GrowthMeasurementTask.STATUS_REJECTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("任务已提交或已主管确认，不能" + action + "，状态=" + task.getStatus());
        }
    }

    private GrowthMeasurementSample findSample(Long taskId, Integer seqNo) {
        GrowthMeasurementSample sample = findSampleNullable(taskId, seqNo);
        if (sample == null) {
            throw new IllegalArgumentException("测量任务下无第" + seqNo + "尾样本");
        }
        return sample;
    }

    private GrowthMeasurementSample findSampleNullable(Long taskId, Integer seqNo) {
        if (seqNo == null) {
            throw new IllegalArgumentException("样本序号不能为空");
        }
        List<GrowthMeasurementSample> rows = sampleMapper.selectList(new QueryWrapper<GrowthMeasurementSample>()
                .eq("task_id", taskId).eq("seq_no", seqNo));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<GrowthMeasurementSample> listSamples(Long taskId) {
        return sampleMapper.selectList(
                new QueryWrapper<GrowthMeasurementSample>().eq("task_id", taskId).orderByAsc("seq_no"));
    }

    private Map<String, Object> handlersOf(GrowthMeasurementTask task) {
        Map<String, Object> handlers = new LinkedHashMap<>();
        handlers.put("measuredBy", task.getMeasuredBy());
        handlers.put("submittedBy", task.getSubmittedBy());
        handlers.put("confirmedBy", task.getConfirmedBy());
        handlers.put("confirmedTime", task.getConfirmedTime());
        return handlers;
    }

    private Map<String, Object> planNoOf(GrowthSamplingPlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", plan.getId());
        m.put("planNo", plan.getPlanNo());
        m.put("planName", plan.getPlanName());
        m.put("sampleCount", plan.getSampleCount());
        m.put("measureIntervalDays", plan.getMeasureIntervalDays());
        m.put("allowedDeviationPct", plan.getAllowedDeviationPct());
        m.put("deviceCode", plan.getDeviceCode());
        m.put("coeffVersionId", plan.getCoeffVersionId());
        return m;
    }

    private GrowthMeasurementTask copyOf(GrowthMeasurementTask s) {
        GrowthMeasurementTask c = new GrowthMeasurementTask();
        c.setId(s.getId());
        c.setTaskNo(s.getTaskNo());
        c.setPlanId(s.getPlanId());
        c.setPondId(s.getPondId());
        c.setBatchId(s.getBatchId());
        c.setMeasureDate(s.getMeasureDate());
        c.setSampleCount(s.getSampleCount());
        c.setAllowedDeviationPct(s.getAllowedDeviationPct());
        c.setDeviceCode(s.getDeviceCode());
        c.setCoeffVersionId(s.getCoeffVersionId());
        c.setMeasuredBy(s.getMeasuredBy());
        c.setRawAvgWeightG(s.getRawAvgWeightG());
        c.setRawAvgLengthMm(s.getRawAvgLengthMm());
        c.setCorrectedAvgWeightG(s.getCorrectedAvgWeightG());
        c.setCorrectedAvgLengthMm(s.getCorrectedAvgLengthMm());
        c.setPredictedHarvestKg(s.getPredictedHarvestKg());
        c.setPredictedHarvestDate(s.getPredictedHarvestDate());
        c.setTotalCount(s.getTotalCount());
        c.setStatus(s.getStatus());
        c.setSubmittedBy(s.getSubmittedBy());
        c.setSubmittedTime(s.getSubmittedTime());
        c.setConfirmedBy(s.getConfirmedBy());
        c.setConfirmedTime(s.getConfirmedTime());
        c.setConfirmRemark(s.getConfirmRemark());
        c.setRejectRemark(s.getRejectRemark());
        c.setRequestId(s.getRequestId());
        c.setRemark(s.getRemark());
        return c;
    }

    private GrowthMeasurementSample copyOfSample(GrowthMeasurementSample s) {
        GrowthMeasurementSample c = new GrowthMeasurementSample();
        c.setId(s.getId());
        c.setTaskId(s.getTaskId());
        c.setSeqNo(s.getSeqNo());
        c.setRawWeightG(s.getRawWeightG());
        c.setRawLengthMm(s.getRawLengthMm());
        c.setWeightCoeff(s.getWeightCoeff());
        c.setLengthCoeff(s.getLengthCoeff());
        c.setCoeffVersionId(s.getCoeffVersionId());
        c.setCorrectedWeightG(s.getCorrectedWeightG());
        c.setCorrectedLengthMm(s.getCorrectedLengthMm());
        c.setAbnormalityFlag(s.getAbnormalityFlag());
        c.setReviewReason(s.getReviewReason());
        c.setReviewedBy(s.getReviewedBy());
        c.setReviewedTime(s.getReviewedTime());
        c.setIncludedFlag(s.getIncludedFlag());
        return c;
    }
}
