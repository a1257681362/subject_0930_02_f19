package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.PlanAdjustRequest;
import com.evops.aqua.maint.dto.MaintPlanCreateRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.PlanPart;
import com.evops.aqua.maint.mapper.MaintChangeEvidenceMapper;
import com.evops.aqua.maint.mapper.MaintPlanMapper;
import com.evops.aqua.maint.mapper.PlanPartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 保养计划：一设备一行，运行小时与日历双轨。
 * 计划调整只改计划本身（version_no 递增）并影响此后生成的工单；已生成工单冻结旧版本快照，绝不回改。
 */
@Service
public class PlanService {

    private final MaintPlanMapper planMapper;
    private final PlanPartMapper planPartMapper;
    private final MaintChangeEvidenceMapper evidenceMapper;
    private final MaintDeviceService deviceService;
    private final SpareService spareService;
    private final MaintEvidenceService evidenceService;

    public PlanService(MaintPlanMapper planMapper,
                       PlanPartMapper planPartMapper,
                       MaintChangeEvidenceMapper evidenceMapper,
                       MaintDeviceService deviceService,
                       SpareService spareService,
                       MaintEvidenceService evidenceService) {
        this.planMapper = planMapper;
        this.planPartMapper = planPartMapper;
        this.evidenceMapper = evidenceMapper;
        this.deviceService = deviceService;
        this.spareService = spareService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintPlan create(MaintPlanCreateRequest request) {
        MaintPlan byRequest = planMapper.selectOne(
                new QueryWrapper<MaintPlan>().eq("request_id", request.getRequestId()));
        if (byRequest != null) {
            return byRequest;
        }
        Long duplicatedNo = planMapper.selectCount(
                new QueryWrapper<MaintPlan>().eq("plan_no", request.getPlanNo()));
        if (duplicatedNo != null && duplicatedNo > 0) {
            throw new IllegalArgumentException("保养计划编号已存在：" + request.getPlanNo());
        }
        MaintDevice device = deviceService.getOrThrow(request.getDeviceId());
        Long duplicatedDevice = planMapper.selectCount(
                new QueryWrapper<MaintPlan>().eq("device_id", device.getId()));
        if (duplicatedDevice != null && duplicatedDevice > 0) {
            throw new IllegalArgumentException("设备[" + device.getDeviceCode() + "]已存在保养计划，一设备只允许一个计划");
        }
        boolean hours = Boolean.TRUE.equals(request.getHoursEnabled());
        boolean calendar = Boolean.TRUE.equals(request.getCalendarEnabled());
        if (!hours && !calendar) {
            throw new IllegalArgumentException("运行小时轨与日历周期轨至少启用一条");
        }
        if (hours && (request.getIntervalHours() == null
                || request.getIntervalHours().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("启用运行小时轨必须填写大于0的间隔小时");
        }
        if (calendar && (request.getIntervalDays() == null || request.getIntervalDays() <= 0)) {
            throw new IllegalArgumentException("启用日历周期轨必须填写大于0的间隔天数");
        }

        MaintPlan plan = new MaintPlan();
        plan.setPlanNo(request.getPlanNo());
        plan.setDeviceId(device.getId());
        plan.setPlanName(request.getPlanName());
        plan.setHoursEnabled(hours ? 1 : 0);
        plan.setIntervalHours(hours ? request.getIntervalHours() : null);
        // 小时基准从设备当前累计读数起算：此前运行不追溯补单
        plan.setLastGeneratedReading(device.getRunningHours());
        plan.setCalendarEnabled(calendar ? 1 : 0);
        plan.setIntervalDays(calendar ? request.getIntervalDays() : null);
        plan.setAnchorDate(calendar ? request.getAnchorDate() : null);
        // 日历基准从起算日开始，首个到期日 = anchorDate + intervalDays
        plan.setLastGeneratedDate(calendar ? request.getAnchorDate() : null);
        plan.setVersionNo(1);
        plan.setStatus(MaintPlan.STATUS_ACTIVE);
        plan.setRequestId(request.getRequestId());
        plan.setRemark(request.getRemark());
        planMapper.insert(plan);

        List<Long> catalogs = new ArrayList<>();
        if (request.getParts() != null) {
            for (MaintPlanCreateRequest.PartLine line : request.getParts()) {
                if (line.getQuantity() == null || line.getQuantity() <= 0) {
                    throw new IllegalArgumentException("备件模板数量必须大于0");
                }
                spareService.getCatalogOrThrow(line.getCatalogId());
                if (catalogs.contains(line.getCatalogId())) {
                    throw new IllegalArgumentException("计划备件模板品类重复，catalogId=" + line.getCatalogId());
                }
                catalogs.add(line.getCatalogId());
                PlanPart planPart = new PlanPart();
                planPart.setPlanId(plan.getId());
                planPart.setCatalogId(line.getCatalogId());
                planPart.setQuantity(line.getQuantity());
                planPartMapper.insert(planPart);
            }
        }

        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_CREATE_PLAN, request.getRequestId(), "计划管理员",
                null, plan, "创建保养计划" + plan.getPlanNo() + "：小时轨=" + (hours ? "开("
                        + request.getIntervalHours() + "h)" : "关") + "，日历轨=" + (calendar
                        ? "开(每" + request.getIntervalDays() + "天,起算" + request.getAnchorDate() + ")"
                        : "关") + "，备件模板行数=" + catalogs.size());
        return plan;
    }

    /**
     * 调整计划：间隔/开关/起算日可改，version_no 递增。
     * 只影响此后生成的工单——已生成工单按冻结的旧版本执行，基准读数/基准日不因调整回退。
     * requestId 幂等：重复调整请求回放当前计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public MaintPlan adjust(Long planId, PlanAdjustRequest request) {
        MaintPlan existingEvidencePlan = findPlanByAdjustRequest(request.getRequestId());
        if (existingEvidencePlan != null) {
            return existingEvidencePlan;
        }
        MaintPlan before = copyOf(getOrThrow(planId));
        MaintPlan plan = getOrThrow(planId);
        if (!MaintPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("计划已暂停，不能调整：" + plan.getPlanNo());
        }

        boolean hours = request.getHoursEnabled() == null
                ? Integer.valueOf(1).equals(plan.getHoursEnabled())
                : request.getHoursEnabled();
        boolean calendar = request.getCalendarEnabled() == null
                ? Integer.valueOf(1).equals(plan.getCalendarEnabled())
                : request.getCalendarEnabled();
        if (!hours && !calendar) {
            throw new IllegalArgumentException("运行小时轨与日历周期轨至少保留一条启用");
        }
        BigDecimal intervalHours = request.getIntervalHours() == null
                ? plan.getIntervalHours() : request.getIntervalHours();
        Integer intervalDays = request.getIntervalDays() == null
                ? plan.getIntervalDays() : request.getIntervalDays();
        LocalDate anchorDate = request.getAnchorDate() == null
                ? plan.getAnchorDate() : request.getAnchorDate();
        if (hours && (intervalHours == null || intervalHours.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("启用运行小时轨必须保留大于0的间隔小时");
        }
        if (calendar && (intervalDays == null || intervalDays <= 0 || anchorDate == null)) {
            throw new IllegalArgumentException("启用日历周期轨必须保留大于0的间隔天数与起算日");
        }

        MaintDevice device = deviceService.getOrThrow(plan.getDeviceId());
        boolean hoursJustEnabled = hours && !Integer.valueOf(1).equals(plan.getHoursEnabled());
        boolean calendarJustEnabled = calendar && !Integer.valueOf(1).equals(plan.getCalendarEnabled());

        plan.setHoursEnabled(hours ? 1 : 0);
        plan.setIntervalHours(hours ? intervalHours : null);
        plan.setCalendarEnabled(calendar ? 1 : 0);
        plan.setIntervalDays(calendar ? intervalDays : null);
        plan.setAnchorDate(calendar ? anchorDate : null);
        if (hoursJustEnabled) {
            // 新启用小时轨：从设备当前读数起算，不追溯补单
            plan.setLastGeneratedReading(device.getRunningHours());
        }
        if (!hours) {
            plan.setLastGeneratedReading(null);
        }
        if (calendarJustEnabled) {
            // 新启用日历轨：从起算日开始排
            plan.setLastGeneratedDate(anchorDate);
        }
        if (!calendar) {
            plan.setLastGeneratedDate(null);
        }
        plan.setVersionNo(plan.getVersionNo() + 1);
        planMapper.updateById(plan);

        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_ADJUST_PLAN, request.getRequestId(), request.getOperator(),
                before, plan, "调整保养计划（v" + before.getVersionNo() + "->v" + plan.getVersionNo()
                        + "），原因：" + request.getReason()
                        + "。调整只影响未生成工单，已生成工单冻结旧版本快照不回改");
        return plan;
    }

    public MaintPlan getOrThrow(Long id) {
        MaintPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new IllegalArgumentException("保养计划不存在，id=" + id);
        }
        return plan;
    }

    public List<MaintPlan> list(Long deviceId, String status) {
        QueryWrapper<MaintPlan> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return planMapper.selectList(wrapper);
    }

    public List<PlanPart> listParts(Long planId) {
        return planPartMapper.selectList(
                new QueryWrapper<PlanPart>().eq("plan_id", planId).orderByAsc("id"));
    }

    /** 同事务更新基准（由工单生成服务在工单成功落库后调用）。 */
    public void updateBaselines(MaintPlan plan) {
        planMapper.updateById(plan);
    }

    private MaintPlan findPlanByAdjustRequest(String requestId) {
        MaintChangeEvidence evidence = evidenceMapper.selectOne(
                new QueryWrapper<MaintChangeEvidence>()
                        .eq("action", MaintChangeEvidence.ACTION_ADJUST_PLAN)
                        .eq("request_id", requestId)
                        .orderByAsc("id").last("LIMIT 1"));
        return evidence == null ? null : getOrThrow(evidence.getEntityId());
    }

    private MaintPlan copyOf(MaintPlan source) {
        MaintPlan copy = new MaintPlan();
        copy.setId(source.getId());
        copy.setPlanNo(source.getPlanNo());
        copy.setDeviceId(source.getDeviceId());
        copy.setPlanName(source.getPlanName());
        copy.setHoursEnabled(source.getHoursEnabled());
        copy.setIntervalHours(source.getIntervalHours());
        copy.setLastGeneratedReading(source.getLastGeneratedReading());
        copy.setCalendarEnabled(source.getCalendarEnabled());
        copy.setIntervalDays(source.getIntervalDays());
        copy.setAnchorDate(source.getAnchorDate());
        copy.setLastGeneratedDate(source.getLastGeneratedDate());
        copy.setVersionNo(source.getVersionNo());
        copy.setStatus(source.getStatus());
        copy.setRequestId(source.getRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
