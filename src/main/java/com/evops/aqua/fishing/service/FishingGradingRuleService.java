package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.GradingRuleLineRequest;
import com.evops.aqua.fishing.dto.GradingRulePublishRequest;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingGradingRule;
import com.evops.aqua.fishing.entity.FishingGradingRuleLine;
import com.evops.aqua.fishing.mapper.FishingGradingRuleLineMapper;
import com.evops.aqua.fishing.mapper.FishingGradingRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 分级规则版本：按版本保存规格与重量区间（半开区间 [min, max)）。
 * 新版本发布后旧版本自动停用；现场判级永远取当时 ACTIVE 版本。
 */
@Service
public class FishingGradingRuleService {

    private final FishingGradingRuleMapper ruleMapper;
    private final FishingGradingRuleLineMapper lineMapper;
    private final FishingEvidenceService evidenceService;

    public FishingGradingRuleService(FishingGradingRuleMapper ruleMapper,
                                     FishingGradingRuleLineMapper lineMapper,
                                     FishingEvidenceService evidenceService) {
        this.ruleMapper = ruleMapper;
        this.lineMapper = lineMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布新版本：版本号自取最大值+1；同版本内区间不得重叠；发布后旧 ACTIVE 版本停用。
     * requestId 幂等：重复发布返回首次版本，不产生第二条。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingGradingRule publish(GradingRulePublishRequest request) {
        FishingGradingRule duplicated = ruleMapper.selectOne(new QueryWrapper<FishingGradingRule>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        List<GradingRuleLineRequest> lines = new ArrayList<>(request.getLines());
        lines.sort(Comparator.comparing(GradingRuleLineRequest::getMinWeightG));
        BigDecimal prevMax = null;
        List<String> specs = new ArrayList<>();
        for (GradingRuleLineRequest line : lines) {
            if (line.getMinWeightG().compareTo(line.getMaxWeightG()) >= 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode()
                        + "重量区间不合法：下限必须小于上限（半开区间 [min, max)）");
            }
            if (line.getMinWeightG().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "重量区间下限不能为负");
            }
            if (specs.contains(line.getSpecCode())) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "在同一版本内重复");
            }
            if (prevMax != null && line.getMinWeightG().compareTo(prevMax) < 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "重量区间与上一档重叠，区间不得交叉");
            }
            specs.add(line.getSpecCode());
            prevMax = line.getMaxWeightG();
        }

        Integer maxVersion = ruleMapper.selectList(new QueryWrapper<FishingGradingRule>()
                        .orderByDesc("version_no").last("limit 1"))
                .stream().findFirst().map(FishingGradingRule::getVersionNo).orElse(0);
        FishingGradingRule rule = new FishingGradingRule();
        rule.setRuleNo("FGR-V" + (maxVersion + 1));
        rule.setVersionNo(maxVersion + 1);
        rule.setStatus(FishingGradingRule.STATUS_ACTIVE);
        rule.setPublishedBy(request.getPublishedBy());
        rule.setPublishedTime(LocalDateTime.now());
        rule.setRequestId(request.getRequestId());
        rule.setRemark(request.getRemark());
        ruleMapper.insert(rule);

        for (GradingRuleLineRequest line : lines) {
            FishingGradingRuleLine entity = new FishingGradingRuleLine();
            entity.setRuleId(rule.getId());
            entity.setSpecCode(line.getSpecCode());
            entity.setMinWeightG(line.getMinWeightG());
            entity.setMaxWeightG(line.getMaxWeightG());
            lineMapper.insert(entity);
        }

        List<FishingGradingRule> oldActive = ruleMapper.selectList(new QueryWrapper<FishingGradingRule>()
                .eq("status", FishingGradingRule.STATUS_ACTIVE).ne("id", rule.getId()));
        for (FishingGradingRule old : oldActive) {
            FishingGradingRule before = copyOf(old);
            old.setStatus(FishingGradingRule.STATUS_DISABLED);
            ruleMapper.updateById(old);
            evidenceService.record(FishingChangeEvidence.ENTITY_GRADING_RULE, old.getId(),
                    FishingChangeEvidence.ACTION_RULE_DISABLE, request.getRequestId(), request.getPublishedBy(),
                    before, old, "新版本" + rule.getRuleNo() + "发布，旧版本自动停用");
        }
        evidenceService.record(FishingChangeEvidence.ENTITY_GRADING_RULE, rule.getId(),
                FishingChangeEvidence.ACTION_RULE_PUBLISH, request.getRequestId(), request.getPublishedBy(),
                null, rule, "发布分级规则" + rule.getRuleNo() + "，规格档" + lines.size() + "个");
        return rule;
    }

    /**
     * 按当时生效规则判级：实际均重落入哪个规格区间（半开 [min, max)）即哪个规格。
     */
    public FishingGradingRuleLine grade(BigDecimal actualAvgWeightG) {
        FishingGradingRule active = activeRule();
        for (FishingGradingRuleLine line : linesOf(active.getId())) {
            if (actualAvgWeightG.compareTo(line.getMinWeightG()) >= 0
                    && actualAvgWeightG.compareTo(line.getMaxWeightG()) < 0) {
                return line;
            }
        }
        throw new IllegalArgumentException("实际均重" + actualAvgWeightG
                + "g超出当前生效分级规则" + active.getRuleNo() + "的重量区间，无法判级");
    }

    public FishingGradingRule activeRule() {
        FishingGradingRule active = ruleMapper.selectOne(new QueryWrapper<FishingGradingRule>()
                .eq("status", FishingGradingRule.STATUS_ACTIVE).last("limit 1"));
        if (active == null) {
            throw new IllegalArgumentException("当前没有生效的分级规则，请先发布规则版本");
        }
        return active;
    }

    public FishingGradingRuleLine lineOfSpec(Long ruleId, String specCode) {
        return lineMapper.selectOne(new QueryWrapper<FishingGradingRuleLine>()
                .eq("rule_id", ruleId).eq("spec_code", specCode).last("limit 1"));
    }

    public List<FishingGradingRuleLine> linesOf(Long ruleId) {
        return lineMapper.selectList(new QueryWrapper<FishingGradingRuleLine>()
                .eq("rule_id", ruleId).orderByAsc("min_weight_g"));
    }

    public FishingGradingRule getOrThrow(Long id) {
        FishingGradingRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new IllegalArgumentException("分级规则不存在，id=" + id);
        }
        return rule;
    }

    public List<FishingGradingRule> list() {
        return ruleMapper.selectList(new QueryWrapper<FishingGradingRule>().orderByAsc("version_no"));
    }

    private FishingGradingRule copyOf(FishingGradingRule rule) {
        FishingGradingRule copy = new FishingGradingRule();
        org.springframework.beans.BeanUtils.copyProperties(rule, copy);
        return copy;
    }
}
