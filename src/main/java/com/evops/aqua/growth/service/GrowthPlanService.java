package com.evops.aqua.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.growth.dto.GrowthPlanCreateRequest;
import com.evops.aqua.growth.dto.GrowthPlanUpdateRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceCoefficient;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.mapper.GrowthSamplingPlanMapper;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 生长抽样方案：按池塘 + 批次配置抽样数量、测量频次、允许偏差，建立即冻结设备校正系数版本。
 */
@Service
public class GrowthPlanService {

    private final GrowthSamplingPlanMapper planMapper;
    private final GrowthCoefficientService coefficientService;
    private final GrowthEvidenceService evidenceService;
    private final PondService pondService;
    private final SeedlingBatchService batchService;

    public GrowthPlanService(GrowthSamplingPlanMapper planMapper,
                             GrowthCoefficientService coefficientService,
                             GrowthEvidenceService evidenceService,
                             PondService pondService,
                             SeedlingBatchService batchService) {
        this.planMapper = planMapper;
        this.coefficientService = coefficientService;
        this.evidenceService = evidenceService;
        this.pondService = pondService;
        this.batchService = batchService;
    }

    @Transactional(rollbackFor = Exception.class)
    public GrowthSamplingPlan create(GrowthPlanCreateRequest request, String operator) {
        Long duplicated = planMapper.selectCount(
                new QueryWrapper<GrowthSamplingPlan>().eq("plan_no", request.getPlanNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("生长抽样方案编号已存在：" + request.getPlanNo());
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("方案结束日期不能早于开始日期");
        }
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        if (!batch.getPondId().equals(pond.getId())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo()
                    + "当前不在养殖池" + pond.getPondCode() + "，不能按该池批配置抽样方案");
        }
        // 冻结设备系数版本：显式指定则校验属于该设备且生效，否则取该设备当前生效版本
        GrowthDeviceCoefficient coeff = request.getCoeffVersionId() == null
                ? coefficientService.currentActive(request.getDeviceCode())
                : coefficientService.requireActiveForDevice(request.getCoeffVersionId(), request.getDeviceCode());

        GrowthSamplingPlan plan = new GrowthSamplingPlan();
        plan.setPlanNo(request.getPlanNo());
        plan.setPlanName(request.getPlanName());
        plan.setPondId(pond.getId());
        plan.setBatchId(batch.getId());
        plan.setSampleCount(request.getSampleCount());
        plan.setMeasureIntervalDays(request.getMeasureIntervalDays());
        plan.setAllowedDeviationPct(request.getAllowedDeviationPct());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setDeviceCode(coeff.getDeviceCode());
        plan.setCoeffVersionId(coeff.getId());
        plan.setStatus(GrowthSamplingPlan.STATUS_ACTIVE);
        plan.setRemark(request.getRemark());
        planMapper.insert(plan);

        evidenceService.record(GrowthChangeEvidence.ENTITY_PLAN, plan.getId(),
                GrowthChangeEvidence.ACTION_CREATE, null, operator, null, plan,
                "创建生长抽样方案" + plan.getPlanNo() + "：池塘id=" + pond.getId()
                        + "，批次=" + batch.getBatchNo() + "，抽样数=" + plan.getSampleCount()
                        + "，频次=" + plan.getMeasureIntervalDays() + "天，允许偏差="
                        + plan.getAllowedDeviationPct() + "%，设备=" + coeff.getDeviceCode()
                        + "，冻结系数版本id=" + coeff.getId());
        return plan;
    }

    /**
     * 修改方案：仅名称/区间/抽样数/频次/允许偏差/备注可改；池塘、批次、设备、冻结系数版本不可改。
     * 已生成任务的配置在生成时已快照，不受修改影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthSamplingPlan update(Long planId, GrowthPlanUpdateRequest request, String operator) {
        GrowthSamplingPlan before = copyOf(getOrThrow(planId));
        GrowthSamplingPlan plan = getOrThrow(planId);
        if (!GrowthSamplingPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("抽样方案已取消，不能修改：" + plan.getPlanNo());
        }
        if (request.getPlanName() != null) {
            plan.setPlanName(request.getPlanName());
        }
        if (request.getSampleCount() != null) {
            plan.setSampleCount(request.getSampleCount());
        }
        if (request.getMeasureIntervalDays() != null) {
            plan.setMeasureIntervalDays(request.getMeasureIntervalDays());
        }
        if (request.getAllowedDeviationPct() != null) {
            plan.setAllowedDeviationPct(request.getAllowedDeviationPct());
        }
        if (request.getStartDate() != null) {
            plan.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            plan.setEndDate(request.getEndDate());
        }
        if (plan.getEndDate().isBefore(plan.getStartDate())) {
            throw new IllegalArgumentException("方案结束日期不能早于开始日期");
        }
        if (request.getRemark() != null) {
            plan.setRemark(request.getRemark());
        }
        planMapper.updateById(plan);
        evidenceService.record(GrowthChangeEvidence.ENTITY_PLAN, plan.getId(),
                GrowthChangeEvidence.ACTION_UPDATE, null, operator, before, plan, "修改生长抽样方案");
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public GrowthSamplingPlan cancel(Long planId, String operator) {
        GrowthSamplingPlan before = copyOf(getOrThrow(planId));
        GrowthSamplingPlan plan = getOrThrow(planId);
        if (!GrowthSamplingPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            return plan;
        }
        plan.setStatus(GrowthSamplingPlan.STATUS_CANCELLED);
        planMapper.updateById(plan);
        evidenceService.record(GrowthChangeEvidence.ENTITY_PLAN, plan.getId(),
                GrowthChangeEvidence.ACTION_CANCEL, null, operator, before, plan,
                "取消生长抽样方案，已生成任务与测量记录保留不变");
        return plan;
    }

    public GrowthSamplingPlan getOrThrow(Long id) {
        GrowthSamplingPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new IllegalArgumentException("生长抽样方案不存在，id=" + id);
        }
        return plan;
    }

    public List<GrowthSamplingPlan> list(Long pondId, Long batchId) {
        QueryWrapper<GrowthSamplingPlan> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByDesc("id");
        return planMapper.selectList(wrapper);
    }

    private GrowthSamplingPlan copyOf(GrowthSamplingPlan source) {
        GrowthSamplingPlan copy = new GrowthSamplingPlan();
        copy.setId(source.getId());
        copy.setPlanNo(source.getPlanNo());
        copy.setPlanName(source.getPlanName());
        copy.setPondId(source.getPondId());
        copy.setBatchId(source.getBatchId());
        copy.setSampleCount(source.getSampleCount());
        copy.setMeasureIntervalDays(source.getMeasureIntervalDays());
        copy.setAllowedDeviationPct(source.getAllowedDeviationPct());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setDeviceCode(source.getDeviceCode());
        copy.setCoeffVersionId(source.getCoeffVersionId());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
