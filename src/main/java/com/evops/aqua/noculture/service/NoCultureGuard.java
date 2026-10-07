package com.evops.aqua.noculture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.noculture.entity.NoCultureFreeze;
import com.evops.aqua.noculture.entity.NoCultureRule;
import com.evops.aqua.noculture.mapper.NoCultureFreezeMapper;
import com.evops.aqua.noculture.mapper.NoCultureRuleMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 禁养守卫：供台账（投苗/转池/捕捞）调用的只读校验。
 * 只依赖 Mapper 与空间匹配器，不反向依赖台账 Service，避免循环依赖。
 * 两道闸：
 *   1) 批次被任一禁养规则冻结（存在 FROZEN 行）——三入口一律拦截；
 *   2) 操作时点（日期）落入池塘所处禁养范围且在规则生效半开区间 [from,to) 内——拦截。
 * 空间边界重叠命中多条规则时按更严格（strictLevel 最大）规则裁决并提示。
 */
@Component
public class NoCultureGuard {

    private final NoCultureFreezeMapper freezeMapper;
    private final NoCultureRuleMapper ruleMapper;
    private final PondMapper pondMapper;
    private final NoCultureSpatialMatcher spatialMatcher;

    public NoCultureGuard(NoCultureFreezeMapper freezeMapper,
                          NoCultureRuleMapper ruleMapper,
                          PondMapper pondMapper,
                          NoCultureSpatialMatcher spatialMatcher) {
        this.freezeMapper = freezeMapper;
        this.ruleMapper = ruleMapper;
        this.pondMapper = pondMapper;
        this.spatialMatcher = spatialMatcher;
    }

    /**
     * 批次存在任意在效冻结行则拒绝。冻结账表示空间约束绑定，是否真正拦截再叠加规则时效：
     * 规则为 ACTIVE 且操作日期落入半开生效区间 [from,to) 才阻断——
     * 未来生效不提前拦、到期自动放行（撤销解冻另见补偿账）。
     */
    public void requireBatchNotFrozen(Long batchId, LocalDate actionDate, String action) {
        List<NoCultureFreeze> blocking = new ArrayList<>();
        for (NoCultureFreeze freeze : activeFreezesOfBatch(batchId)) {
            NoCultureRule rule = ruleMapper.selectById(freeze.getRuleId());
            if (rule != null && NoCultureRule.STATUS_ACTIVE.equals(rule.getStatus())
                    && inEffect(rule, actionDate)) {
                blocking.add(freeze);
            }
        }
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("批次id=" + batchId + "已被海区禁养规则冻结（规则id："
                    + joinRuleIds(blocking) + "），" + actionDate + "处于禁养生效期，禁止" + action
                    + "；须待规则撤销/到期解冻后再操作");
        }
    }

    /**
     * 三入口空间 + 时间校验：池塘在操作日期命中任一在效禁养规则即拒绝，
     * 命中多条时按更严格规则（strictLevel 最大）提示。
     */
    public void requireActionAllowed(Long pondId, LocalDate actionDate, String action) {
        if (pondId == null || actionDate == null) {
            return;
        }
        Pond pond = pondMapper.selectById(pondId);
        if (pond == null) {
            return;
        }
        List<NoCultureRule> hit = new ArrayList<>();
        for (NoCultureRule rule : activeRules()) {
            if (inEffect(rule, actionDate) && spatialMatcher.hits(rule, pond)) {
                hit.add(rule);
            }
        }
        if (hit.isEmpty()) {
            return;
        }
        NoCultureRule strictest = hit.get(0);
        for (NoCultureRule rule : hit) {
            if (rule.getStrictLevel() != null && strictest.getStrictLevel() != null
                    && rule.getStrictLevel() > strictest.getStrictLevel()) {
                strictest = rule;
            }
        }
        throw new IllegalArgumentException("池塘" + pond.getPondCode() + "在" + actionDate
                + "处于禁养范围（按更严格规则" + strictest.getRuleNo() + "，依据文件《"
                + strictest.getBasisDocName() + "》" + strictest.getBasisDocNo()
                + "），禁止" + action + "；共命中" + hit.size() + "条禁养规则");
    }

    /** 返回批次当前仍 FROZEN 的冻结账（供服务层补偿判断与查询）。 */
    public List<NoCultureFreeze> activeFreezesOfBatch(Long batchId) {
        return freezeMapper.selectList(new QueryWrapper<NoCultureFreeze>()
                .eq("batch_id", batchId)
                .eq("status", NoCultureFreeze.STATUS_FROZEN)
                .orderByAsc("id"));
    }

    /** 返回操作日期在效（ACTIVE 且日期落入半开区间）的全部规则。 */
    public List<NoCultureRule> activeRulesAt(LocalDate date) {
        List<NoCultureRule> all = activeRules();
        List<NoCultureRule> inEffect = new ArrayList<>();
        for (NoCultureRule rule : all) {
            if (inEffect(rule, date)) {
                inEffect.add(rule);
            }
        }
        return inEffect;
    }

    private List<NoCultureRule> activeRules() {
        return ruleMapper.selectList(new QueryWrapper<NoCultureRule>()
                .eq("status", NoCultureRule.STATUS_ACTIVE)
                .orderByAsc("id"));
    }

    /** 半开区间 [from,to)：from <= date < to；to 为空表示长期有效。 */
    private boolean inEffect(NoCultureRule rule, LocalDate date) {
        if (date.isBefore(rule.getEffectiveFrom())) {
            return false;
        }
        return rule.getEffectiveTo() == null || date.isBefore(rule.getEffectiveTo());
    }

    private String joinRuleIds(List<NoCultureFreeze> freezes) {
        StringBuilder sb = new StringBuilder();
        for (NoCultureFreeze freeze : freezes) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(freeze.getRuleId());
        }
        return sb.toString();
    }
}
