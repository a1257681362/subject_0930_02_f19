package com.evops.aqua.release.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.release.dto.ReleaseRulePublishRequest;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.entity.ReleaseRuleVersion;
import com.evops.aqua.release.entity.ReleaseThresholdRule;
import com.evops.aqua.release.mapper.ReleaseRuleVersionMapper;
import com.evops.aqua.release.mapper.ReleaseThresholdRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 出池检验阈值规则版本：检验项目按「品种 + 用途」组合逐指标配置。
 * 新版本发布时旧版本停用；已建立检验单冻结建单时版本，历史检验结论不受新版本影响。
 */
@Service
public class ReleaseRuleVersionService {

    private final ReleaseRuleVersionMapper versionMapper;
    private final ReleaseThresholdRuleMapper thresholdMapper;
    private final ReleaseEvidenceService evidenceService;

    public ReleaseRuleVersionService(ReleaseRuleVersionMapper versionMapper,
                                     ReleaseThresholdRuleMapper thresholdMapper,
                                     ReleaseEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.thresholdMapper = thresholdMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReleaseRuleVersion publish(ReleaseRulePublishRequest request) {
        Long duplicated = versionMapper.selectCount(new QueryWrapper<ReleaseRuleVersion>()
                .eq("version_no", request.getVersionNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("规则版本号已存在：" + request.getVersionNo());
        }
        validateThresholds(request);

        ReleaseRuleVersion version = new ReleaseRuleVersion();
        version.setVersionNo(request.getVersionNo());
        version.setStatus(ReleaseRuleVersion.STATUS_ACTIVE);
        version.setRemark(request.getRemark());
        versionMapper.insert(version);

        for (ReleaseRulePublishRequest.ThresholdDto dto : request.getThresholds()) {
            ReleaseThresholdRule rule = new ReleaseThresholdRule();
            rule.setVersionId(version.getId());
            rule.setSpeciesCode(dto.getSpeciesCode().trim());
            rule.setSpeciesName(dto.getSpeciesName().trim());
            rule.setUsageCode(dto.getUsageCode().trim());
            rule.setUsageName(dto.getUsageName().trim());
            rule.setIndicatorCode(dto.getIndicatorCode().trim());
            rule.setIndicatorName(dto.getIndicatorName().trim());
            rule.setUnit(dto.getUnit());
            rule.setMinValue(dto.getMinValue());
            rule.setMaxValue(dto.getMaxValue());
            rule.setRequiredFlag(dto.getRequiredFlag() == null ? Boolean.TRUE : dto.getRequiredFlag());
            thresholdMapper.insert(rule);
        }

        // 旧生效版本停用（同一时刻仅一个 ACTIVE）
        List<ReleaseRuleVersion> actives = versionMapper.selectList(new QueryWrapper<ReleaseRuleVersion>()
                .eq("status", ReleaseRuleVersion.STATUS_ACTIVE)
                .ne("id", version.getId()));
        for (ReleaseRuleVersion old : actives) {
            ReleaseRuleVersion before = copyOf(old);
            old.setStatus(ReleaseRuleVersion.STATUS_SUPERSEDED);
            versionMapper.updateById(old);
            evidenceService.record(ReleaseChangeEvidence.ENTITY_RULE_VERSION, old.getId(),
                    ReleaseChangeEvidence.ACTION_UPDATE, null,
                    ReleaseEvidenceService.currentOperator(), before, old,
                    "发布新版本" + version.getVersionNo() + "，旧版本停用");
        }

        evidenceService.record(ReleaseChangeEvidence.ENTITY_RULE_VERSION, version.getId(),
                ReleaseChangeEvidence.ACTION_CREATE, null,
                ReleaseEvidenceService.currentOperator(), null, version,
                "发布出池检验阈值规则版本" + version.getVersionNo()
                        + "，共" + request.getThresholds().size() + "条品种+用途指标阈值");
        return version;
    }

    public ReleaseRuleVersion getOrThrow(Long id) {
        ReleaseRuleVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("出池检验规则版本不存在，id=" + id);
        }
        return version;
    }

    public ReleaseRuleVersion requireActive(Long id) {
        ReleaseRuleVersion version = getOrThrow(id);
        if (!ReleaseRuleVersion.STATUS_ACTIVE.equals(version.getStatus())) {
            throw new IllegalArgumentException("规则版本" + version.getVersionNo() + "已停用，不能用于新检验单");
        }
        return version;
    }

    /** 当前生效版本：取最新一条 ACTIVE。 */
    public ReleaseRuleVersion currentActive() {
        List<ReleaseRuleVersion> actives = versionMapper.selectList(new QueryWrapper<ReleaseRuleVersion>()
                .eq("status", ReleaseRuleVersion.STATUS_ACTIVE)
                .orderByDesc("id").last("LIMIT 1"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("尚无生效的出池检验阈值规则版本，请先发布");
        }
        return actives.get(0);
    }

    public List<ReleaseThresholdRule> listThresholds(Long versionId) {
        return thresholdMapper.selectList(new QueryWrapper<ReleaseThresholdRule>()
                .eq("version_id", versionId).orderByAsc("id"));
    }

    /** 取某版本下「品种 + 用途」组合的全部指标阈值。 */
    public List<ReleaseThresholdRule> thresholdsOf(Long versionId, String speciesCode, String usageCode) {
        return thresholdMapper.selectList(new QueryWrapper<ReleaseThresholdRule>()
                .eq("version_id", versionId)
                .eq("species_code", speciesCode)
                .eq("usage_code", usageCode)
                .orderByAsc("id"));
    }

    /** 在指定版本中解析「品种 + 用途」组合（任取一行带出名称），无配置抛异常。 */
    public ReleaseThresholdRule resolveCombo(Long versionId, String speciesCode, String usageCode) {
        List<ReleaseThresholdRule> rules = thresholdsOf(versionId, speciesCode, usageCode);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("生效规则版本id=" + versionId
                    + "未配置品种[" + speciesCode + "]用途[" + usageCode + "]组合的检验阈值，无法建检验单");
        }
        return rules.get(0);
    }

    private void validateThresholds(ReleaseRulePublishRequest request) {
        Set<String> seen = new HashSet<>();
        for (ReleaseRulePublishRequest.ThresholdDto dto : request.getThresholds()) {
            if (dto.getMinValue() != null && dto.getMaxValue() != null
                    && dto.getMinValue().compareTo(dto.getMaxValue()) > 0) {
                throw new IllegalArgumentException("品种[" + dto.getSpeciesCode() + "]用途[" + dto.getUsageCode()
                        + "]指标[" + dto.getIndicatorCode() + "]阈值下限不能大于上限");
            }
            String key = dto.getSpeciesCode().trim() + "|" + dto.getUsageCode().trim()
                    + "|" + dto.getIndicatorCode().trim();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("同一版本内品种+用途+指标重复：" + key);
            }
        }
    }

    private ReleaseRuleVersion copyOf(ReleaseRuleVersion source) {
        ReleaseRuleVersion copy = new ReleaseRuleVersion();
        copy.setId(source.getId());
        copy.setVersionNo(source.getVersionNo());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }

    public List<ReleaseRuleVersion> listVersions() {
        return versionMapper.selectList(new QueryWrapper<ReleaseRuleVersion>().orderByDesc("id"));
    }

    /** 版本下涉及的全部「品种 + 用途」组合（去重，保序）。 */
    public List<String> combosOf(Long versionId) {
        List<String> combos = new ArrayList<>();
        for (ReleaseThresholdRule rule : listThresholds(versionId)) {
            String combo = rule.getSpeciesCode() + "|" + rule.getUsageCode();
            if (!combos.contains(combo)) {
                combos.add(combo);
            }
        }
        return combos;
    }
}
