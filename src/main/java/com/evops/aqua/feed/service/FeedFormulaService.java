package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.dto.FormulaPublishRequest;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedFormulaRule;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.mapper.FeedFormulaRuleMapper;
import com.evops.aqua.feed.mapper.FeedFormulaVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 投喂配方版本：按苗种发布；版本下按 日龄区间 + 水温区间 选择饲料与计划投喂量。
 * 配方调整以新版本发布，同苗种旧 ACTIVE 版本置 SUPERSEDED；
 * 已建立的投喂单冻结其版本快照，历史投喂永远引用旧版本规则，不受新版本影响。
 */
@Service
public class FeedFormulaService {

    private final FeedFormulaVersionMapper versionMapper;
    private final FeedFormulaRuleMapper ruleMapper;
    private final FeedEvidenceService evidenceService;

    public FeedFormulaService(FeedFormulaVersionMapper versionMapper,
                              FeedFormulaRuleMapper ruleMapper,
                              FeedEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.ruleMapper = ruleMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布配方版本：写版本 + 规则行快照；同苗种旧生效版本停用。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedFormulaVersion publish(FormulaPublishRequest request) {
        Long duplicated = versionMapper.selectCount(
                new QueryWrapper<FeedFormulaVersion>().eq("version_no", request.getVersionNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("配方版本号已存在：" + request.getVersionNo());
        }
        int sort = 0;
        for (FormulaPublishRequest.RuleDto rule : request.getRules()) {
            validateRule(rule, request.getVersionNo());
        }

        FeedFormulaVersion version = new FeedFormulaVersion();
        version.setVersionNo(request.getVersionNo());
        version.setSpecies(request.getSpecies());
        version.setStatus(FeedFormulaVersion.STATUS_ACTIVE);
        version.setRemark(request.getRemark());
        versionMapper.insert(version);

        for (FormulaPublishRequest.RuleDto dto : request.getRules()) {
            FeedFormulaRule rule = new FeedFormulaRule();
            rule.setVersionId(version.getId());
            rule.setSpecies(request.getSpecies());
            rule.setDayAgeFrom(dto.getDayAgeFrom());
            rule.setDayAgeTo(dto.getDayAgeTo());
            rule.setWaterTempFrom(dto.getWaterTempFrom());
            rule.setWaterTempTo(dto.getWaterTempTo());
            rule.setFeedName(dto.getFeedName());
            rule.setPlannedQtyKg(dto.getPlannedQtyKg());
            rule.setSortNo(dto.getSortNo() == null ? sort : dto.getSortNo());
            ruleMapper.insert(rule);
            sort++;
        }

        // 同苗种旧生效版本停用
        List<FeedFormulaVersion> actives = versionMapper.selectList(new QueryWrapper<FeedFormulaVersion>()
                .eq("species", request.getSpecies())
                .eq("status", FeedFormulaVersion.STATUS_ACTIVE)
                .ne("id", version.getId()));
        for (FeedFormulaVersion old : actives) {
            FeedFormulaVersion before = copyOf(old);
            old.setStatus(FeedFormulaVersion.STATUS_SUPERSEDED);
            versionMapper.updateById(old);
            evidenceService.record(FeedChangeEvidence.ENTITY_FORMULA_VERSION, old.getId(),
                    FeedChangeEvidence.ACTION_UPDATE, null, before, old,
                    "苗种[" + request.getSpecies() + "]发布新版本" + version.getVersionNo()
                            + "，旧版本" + old.getVersionNo() + "停用；历史投喂仍引用旧快照");
        }

        evidenceService.record(FeedChangeEvidence.ENTITY_FORMULA_VERSION, version.getId(),
                FeedChangeEvidence.ACTION_CREATE, null, null, version,
                "发布配方版本" + version.getVersionNo() + "（苗种" + request.getSpecies() + "，规则"
                        + request.getRules().size() + "条）");
        return version;
    }

    public FeedFormulaVersion getOrThrow(Long id) {
        FeedFormulaVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("配方版本不存在，id=" + id);
        }
        return version;
    }

    /** 某苗种当前生效版本。 */
    public FeedFormulaVersion currentActive(String species) {
        List<FeedFormulaVersion> actives = versionMapper.selectList(new QueryWrapper<FeedFormulaVersion>()
                .eq("species", species)
                .eq("status", FeedFormulaVersion.STATUS_ACTIVE)
                .orderByDesc("id").last("LIMIT 1"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("苗种[" + species + "]尚无生效投喂配方版本，请先发布");
        }
        return actives.get(0);
    }

    public List<FeedFormulaRule> listRules(Long versionId) {
        return ruleMapper.selectList(
                new QueryWrapper<FeedFormulaRule>().eq("version_id", versionId)
                        .orderByAsc("sort_no", "id"));
    }

    /**
     * 在指定版本快照内按 日龄 + 水温 匹配规则。命中多行取区间最窄（最具体）的一行。
     * 日龄以 (投喂日 - 投苗日) 推算；水温闭区间命中。
     */
    public FeedFormulaRule matchRule(Long versionId, int dayAge, BigDecimal waterTemp) {
        List<FeedFormulaRule> rules = listRules(versionId);
        FeedFormulaRule best = null;
        for (FeedFormulaRule rule : rules) {
            boolean ageHit = dayAge >= rule.getDayAgeFrom() && dayAge <= rule.getDayAgeTo();
            boolean tempHit = waterTemp.compareTo(rule.getWaterTempFrom()) >= 0
                    && waterTemp.compareTo(rule.getWaterTempTo()) <= 0;
            if (ageHit && tempHit) {
                if (best == null || specificity(rule) < specificity(best)) {
                    best = rule;
                }
            }
        }
        if (best == null) {
            throw new IllegalArgumentException(
                    "配方版本id=" + versionId + "下无匹配规则：日龄=" + dayAge + "，水温=" + waterTemp);
        }
        return best;
    }

    /** 区间宽度：日龄跨度 × 大系数 + 水温跨度，越小越具体。 */
    private long specificity(FeedFormulaRule rule) {
        long ageSpan = (long) (rule.getDayAgeTo() - rule.getDayAgeFrom());
        long tempSpan = rule.getWaterTempTo().subtract(rule.getWaterTempFrom())
                .multiply(BigDecimal.valueOf(100)).longValue();
        return ageSpan * 100000L + tempSpan;
    }

    private void validateRule(FormulaPublishRequest.RuleDto rule, String versionNo) {
        if (rule.getDayAgeFrom() < 0 || rule.getDayAgeTo() < rule.getDayAgeFrom()) {
            throw new IllegalArgumentException(
                    "版本" + versionNo + "存在非法日龄区间：" + rule.getDayAgeFrom() + "~" + rule.getDayAgeTo());
        }
        if (rule.getWaterTempFrom().compareTo(rule.getWaterTempTo()) > 0) {
            throw new IllegalArgumentException(
                    "版本" + versionNo + "存在非法水温区间：" + rule.getWaterTempFrom()
                            + "~" + rule.getWaterTempTo());
        }
    }

    private FeedFormulaVersion copyOf(FeedFormulaVersion source) {
        FeedFormulaVersion copy = new FeedFormulaVersion();
        copy.setId(source.getId());
        copy.setVersionNo(source.getVersionNo());
        copy.setSpecies(source.getSpecies());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
