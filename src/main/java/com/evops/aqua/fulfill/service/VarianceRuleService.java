package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fulfill.dto.VarianceRulePublishRequest;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.VarianceRule;
import com.evops.aqua.fulfill.entity.VarianceRuleLine;
import com.evops.aqua.fulfill.mapper.VarianceRuleLineMapper;
import com.evops.aqua.fulfill.mapper.VarianceRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 净重差异规则版本：按规格配置容忍百分比 + 绝对容差。
 * 新版本发布后旧版本自动停用；交接永远取当时 ACTIVE 版本并冻结快照。
 */
@Service
public class VarianceRuleService {

    private final VarianceRuleMapper ruleMapper;
    private final VarianceRuleLineMapper lineMapper;
    private final FulfillEvidenceService evidenceService;

    public VarianceRuleService(VarianceRuleMapper ruleMapper,
                               VarianceRuleLineMapper lineMapper,
                               FulfillEvidenceService evidenceService) {
        this.ruleMapper = ruleMapper;
        this.lineMapper = lineMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public VarianceRule publish(VarianceRulePublishRequest request) {
        VarianceRule duplicated = ruleMapper.selectOne(new QueryWrapper<VarianceRule>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        List<String> specs = new ArrayList<>();
        for (VarianceRulePublishRequest.Line line : request.getLines()) {
            if (specs.contains(line.getSpecCode())) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "在同一版本内重复");
            }
            if (line.getTolerancePct().signum() < 0 || line.getTolerancePct().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "容忍百分比必须在0~100之间");
            }
            if (line.getToleranceAbsKg().signum() < 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "绝对容差不能为负");
            }
            specs.add(line.getSpecCode());
        }

        Integer maxVersion = ruleMapper.selectList(new QueryWrapper<VarianceRule>()
                        .orderByDesc("version_no").last("limit 1"))
                .stream().findFirst().map(VarianceRule::getVersionNo).orElse(0);
        VarianceRule rule = new VarianceRule();
        rule.setRuleNo("FVR-V" + (maxVersion + 1));
        rule.setVersionNo(maxVersion + 1);
        rule.setStatus(VarianceRule.STATUS_ACTIVE);
        rule.setPublishedBy(request.getPublishedBy());
        rule.setPublishedTime(LocalDateTime.now());
        rule.setRequestId(request.getRequestId());
        rule.setRemark(request.getRemark());
        ruleMapper.insert(rule);

        for (VarianceRulePublishRequest.Line line : request.getLines()) {
            VarianceRuleLine entity = new VarianceRuleLine();
            entity.setRuleId(rule.getId());
            entity.setSpecCode(line.getSpecCode());
            entity.setTolerancePct(line.getTolerancePct());
            entity.setToleranceAbsKg(line.getToleranceAbsKg());
            lineMapper.insert(entity);
        }

        List<VarianceRule> oldActive = ruleMapper.selectList(new QueryWrapper<VarianceRule>()
                .eq("status", VarianceRule.STATUS_ACTIVE).ne("id", rule.getId()));
        for (VarianceRule old : oldActive) {
            VarianceRule before = copyOf(old);
            old.setStatus(VarianceRule.STATUS_DISABLED);
            ruleMapper.updateById(old);
            evidenceService.record(FulfillChangeEvidence.ENTITY_VARIANCE_RULE, old.getId(),
                    FulfillChangeEvidence.ACTION_RULE_DISABLE, request.getRequestId(), request.getPublishedBy(),
                    before, old, "新版本" + rule.getRuleNo() + "发布，旧版本自动停用");
        }
        evidenceService.record(FulfillChangeEvidence.ENTITY_VARIANCE_RULE, rule.getId(),
                FulfillChangeEvidence.ACTION_RULE_PUBLISH, request.getRequestId(), request.getPublishedBy(),
                null, rule, "发布净重差异规则" + rule.getRuleNo() + "，规格档"
                        + request.getLines().size() + "个");
        return rule;
    }

    public VarianceRule activeRule() {
        VarianceRule active = ruleMapper.selectOne(new QueryWrapper<VarianceRule>()
                .eq("status", VarianceRule.STATUS_ACTIVE).last("limit 1"));
        if (active == null) {
            throw new IllegalArgumentException("当前没有生效的净重差异规则，请先发布规则版本");
        }
        return active;
    }

    public VarianceRuleLine lineOfSpec(Long ruleId, String specCode) {
        return lineMapper.selectOne(new QueryWrapper<VarianceRuleLine>()
                .eq("rule_id", ruleId).eq("spec_code", specCode).last("limit 1"));
    }

    public List<VarianceRuleLine> linesOf(Long ruleId) {
        return lineMapper.selectList(new QueryWrapper<VarianceRuleLine>()
                .eq("rule_id", ruleId).orderByAsc("id"));
    }

    /**
     * 规则快照：版本号/规则号 + 各规格阈值，冻结到交接单。
     */
    public Map<String, Object> snapshotOf(VarianceRule rule) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ruleNo", rule.getRuleNo());
        snapshot.put("versionNo", rule.getVersionNo());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (VarianceRuleLine line : linesOf(rule.getId())) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("specCode", line.getSpecCode());
            l.put("tolerancePct", line.getTolerancePct());
            l.put("toleranceAbsKg", line.getToleranceAbsKg());
            lines.add(l);
        }
        snapshot.put("lines", lines);
        return snapshot;
    }

    public List<VarianceRule> list() {
        return ruleMapper.selectList(new QueryWrapper<VarianceRule>().orderByAsc("version_no"));
    }

    private VarianceRule copyOf(VarianceRule rule) {
        VarianceRule copy = new VarianceRule();
        org.springframework.beans.BeanUtils.copyProperties(rule, copy);
        return copy;
    }
}
