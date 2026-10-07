package com.evops.aqua.wq.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.wq.dto.PlanCreateRequest;
import com.evops.aqua.wq.dto.PlanUpdateRequest;
import com.evops.aqua.wq.entity.WqChangeEvidence;
import com.evops.aqua.wq.entity.WqPlan;
import com.evops.aqua.wq.entity.WqPlanPond;
import com.evops.aqua.wq.entity.WqRuleVersion;
import com.evops.aqua.wq.mapper.WqPlanMapper;
import com.evops.aqua.wq.mapper.WqPlanPondMapper;
import com.evops.aqua.service.PondService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 水质采样计划：绑定规则版本与养殖阶段，按所选池塘生成采样任务。
 */
@Service
public class WqPlanService {

    private final WqPlanMapper planMapper;
    private final WqPlanPondMapper planPondMapper;
    private final WqRuleVersionService ruleVersionService;
    private final WqEvidenceService evidenceService;
    private final PondService pondService;

    public WqPlanService(WqPlanMapper planMapper,
                         WqPlanPondMapper planPondMapper,
                         WqRuleVersionService ruleVersionService,
                         WqEvidenceService evidenceService,
                         PondService pondService) {
        this.planMapper = planMapper;
        this.planPondMapper = planPondMapper;
        this.ruleVersionService = ruleVersionService;
        this.evidenceService = evidenceService;
        this.pondService = pondService;
    }

    @Transactional(rollbackFor = Exception.class)
    public WqPlan create(PlanCreateRequest request) {
        Long duplicated = planMapper.selectCount(
                new QueryWrapper<WqPlan>().eq("plan_no", request.getPlanNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("采样计划编号已存在：" + request.getPlanNo());
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("计划结束日期不能早于开始日期");
        }
        WqRuleVersion version = request.getRuleVersionId() == null
                ? ruleVersionService.currentActive()
                : ruleVersionService.requireActive(request.getRuleVersionId());
        // 阶段必须存在于所绑版本
        ruleVersionService.getStageOrThrow(version.getId(), request.getStageCode());

        WqPlan plan = new WqPlan();
        plan.setPlanNo(request.getPlanNo());
        plan.setPlanName(request.getPlanName());
        plan.setRuleVersionId(version.getId());
        plan.setStageCode(request.getStageCode());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus(WqPlan.STATUS_ACTIVE);
        plan.setRemark(request.getRemark());
        planMapper.insert(plan);

        List<Long> distinctPonds = new ArrayList<>();
        for (Long pondId : request.getPondIds()) {
            pondService.requireActive(pondService.getOrThrow(pondId));
            if (!distinctPonds.contains(pondId)) {
                distinctPonds.add(pondId);
            }
        }
        for (Long pondId : distinctPonds) {
            WqPlanPond planPond = new WqPlanPond();
            planPond.setPlanId(plan.getId());
            planPond.setPondId(pondId);
            planPondMapper.insert(planPond);
        }

        evidenceService.record(WqChangeEvidence.ENTITY_PLAN, plan.getId(),
                WqChangeEvidence.ACTION_CREATE, null, null, plan,
                "创建采样计划" + plan.getPlanNo() + "，覆盖池塘数=" + distinctPonds.size()
                        + "，规则版本id=" + version.getId());
        return plan;
    }

    /**
     * 修改计划：仅名称/日期/备注可改；阶段与规则版本冻结。
     * 已生成任务不受影响（任务生成时已快照）；所有修改留证。
     */
    @Transactional(rollbackFor = Exception.class)
    public WqPlan update(Long planId, PlanUpdateRequest request) {
        WqPlan before = copyOf(getOrThrow(planId));
        WqPlan plan = getOrThrow(planId);
        if (!WqPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("计划已取消，不能修改：" + plan.getPlanNo());
        }
        if (request.getPlanName() != null) {
            plan.setPlanName(request.getPlanName());
        }
        if (request.getStartDate() != null) {
            plan.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            plan.setEndDate(request.getEndDate());
        }
        if (plan.getEndDate().isBefore(plan.getStartDate())) {
            throw new IllegalArgumentException("计划结束日期不能早于开始日期");
        }
        if (request.getRemark() != null) {
            plan.setRemark(request.getRemark());
        }
        planMapper.updateById(plan);
        evidenceService.record(WqChangeEvidence.ENTITY_PLAN, plan.getId(),
                WqChangeEvidence.ACTION_UPDATE, null, before, plan, "修改采样计划");
        return plan;
    }

    public WqPlan getOrThrow(Long id) {
        WqPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new IllegalArgumentException("采样计划不存在，id=" + id);
        }
        return plan;
    }

    public List<WqPlan> list(Long pondId) {
        if (pondId == null) {
            return planMapper.selectList(new QueryWrapper<WqPlan>().orderByAsc("id"));
        }
        List<WqPlanPond> links = planPondMapper.selectList(
                new QueryWrapper<WqPlanPond>().eq("pond_id", pondId));
        List<WqPlan> plans = new ArrayList<>();
        for (WqPlanPond link : links) {
            plans.add(getOrThrow(link.getPlanId()));
        }
        return plans;
    }

    public List<Long> listPondIds(Long planId) {
        List<WqPlanPond> links = planPondMapper.selectList(
                new QueryWrapper<WqPlanPond>().eq("plan_id", planId).orderByAsc("id"));
        List<Long> pondIds = new ArrayList<>();
        for (WqPlanPond link : links) {
            pondIds.add(link.getPondId());
        }
        return pondIds;
    }

    /**
     * 按养殖阶段与状态查在执行计划（新规则版本发布时重绑用）。
     */
    public List<WqPlan> listByStageAndStatus(String stageCode, String status) {
        return planMapper.selectList(new QueryWrapper<WqPlan>()
                .eq("stage_code", stageCode)
                .eq("status", status)
                .orderByAsc("id"));
    }

    /**
     * 供新规则版本重绑调用：仅更新版本指针，证据由调用方记录。
     */
    public void updateRuleVersion(WqPlan plan) {
        planMapper.updateById(plan);
    }

    private WqPlan copyOf(WqPlan source) {
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
