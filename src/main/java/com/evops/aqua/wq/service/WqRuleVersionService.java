package com.evops.aqua.wq.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.wq.dto.RuleVersionPublishRequest;
import com.evops.aqua.wq.entity.WqChangeEvidence;
import com.evops.aqua.wq.entity.WqIndicatorRule;
import com.evops.aqua.wq.entity.WqRuleVersion;
import com.evops.aqua.wq.entity.WqStageRule;
import com.evops.aqua.wq.mapper.WqIndicatorRuleMapper;
import com.evops.aqua.wq.mapper.WqRuleVersionMapper;
import com.evops.aqua.wq.mapper.WqStageRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * 指标规则版本：不同养殖阶段配置采样频率与指标范围。
 * 新版本发布时旧版本停用；已冻结在任务上的版本快照不受影响。
 */
@Service
public class WqRuleVersionService {

    private final WqRuleVersionMapper versionMapper;
    private final WqStageRuleMapper stageRuleMapper;
    private final WqIndicatorRuleMapper indicatorRuleMapper;
    private final WqEvidenceService evidenceService;

    public WqRuleVersionService(WqRuleVersionMapper versionMapper,
                                WqStageRuleMapper stageRuleMapper,
                                WqIndicatorRuleMapper indicatorRuleMapper,
                                WqEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.stageRuleMapper = stageRuleMapper;
        this.indicatorRuleMapper = indicatorRuleMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布新版本：写入版本 + 阶段 + 指标快照，旧 ACTIVE 版本置 SUPERSEDED。
     * 注意：本方法不直接改动已生成任务——任务在生成时冻结版本；
     * 未开始（DRAFT 且从未暂存）任务的重绑由 TaskService#rebindUnstartedTasks 完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public WqRuleVersion publish(RuleVersionPublishRequest request, Consumer<Long> unstartedRebinder) {
        Long duplicated = versionMapper.selectCount(
                new QueryWrapper<WqRuleVersion>().eq("version_no", request.getVersionNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("规则版本号已存在：" + request.getVersionNo());
        }

        WqRuleVersion version = new WqRuleVersion();
        version.setVersionNo(request.getVersionNo());
        version.setStatus(WqRuleVersion.STATUS_ACTIVE);
        version.setRemark(request.getRemark());
        versionMapper.insert(version);

        int sort = 0;
        for (RuleVersionPublishRequest.StageDto stageDto : request.getStages()) {
            validateRange(stageDto, request.getVersionNo());
            WqStageRule stage = new WqStageRule();
            stage.setVersionId(version.getId());
            stage.setStageCode(stageDto.getStageCode());
            stage.setStageName(stageDto.getStageName());
            stage.setSampleIntervalDays(stageDto.getSampleIntervalDays());
            stage.setSortNo(stageDto.getSortNo() == null ? sort : stageDto.getSortNo());
            stageRuleMapper.insert(stage);

            for (RuleVersionPublishRequest.IndicatorDto indDto : stageDto.getIndicators()) {
                WqIndicatorRule indicator = new WqIndicatorRule();
                indicator.setStageRuleId(stage.getId());
                indicator.setIndicatorCode(indDto.getIndicatorCode());
                indicator.setIndicatorName(indDto.getIndicatorName());
                indicator.setUnit(indDto.getUnit());
                indicator.setMinValue(indDto.getMinValue());
                indicator.setMaxValue(indDto.getMaxValue());
                indicator.setRequiredFlag(indDto.getRequiredFlag() == null
                        ? Boolean.TRUE : indDto.getRequiredFlag());
                indicatorRuleMapper.insert(indicator);
            }
            sort++;
        }

        // 旧生效版本停用（只允许一个 ACTIVE）
        List<WqRuleVersion> actives = versionMapper.selectList(
                new QueryWrapper<WqRuleVersion>()
                        .eq("status", WqRuleVersion.STATUS_ACTIVE)
                        .ne("id", version.getId()));
        for (WqRuleVersion old : actives) {
            WqRuleVersion before = copyOf(old);
            old.setStatus(WqRuleVersion.STATUS_SUPERSEDED);
            versionMapper.updateById(old);
            evidenceService.record(WqChangeEvidence.ENTITY_RULE_VERSION, old.getId(),
                    WqChangeEvidence.ACTION_UPDATE, null, before, old,
                    "发布新版本" + version.getVersionNo() + "，旧版本停用");
        }

        evidenceService.record(WqChangeEvidence.ENTITY_RULE_VERSION, version.getId(),
                WqChangeEvidence.ACTION_CREATE, null, null, version,
                "发布规则版本" + version.getVersionNo());

        // 新版本只作用于未开始任务：重绑未开始任务的规则快照
        if (unstartedRebinder != null) {
            unstartedRebinder.accept(version.getId());
        }
        return version;
    }

    public WqRuleVersion getOrThrow(Long id) {
        WqRuleVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("规则版本不存在，id=" + id);
        }
        return version;
    }

    public WqRuleVersion requireActive(Long id) {
        WqRuleVersion version = getOrThrow(id);
        if (!WqRuleVersion.STATUS_ACTIVE.equals(version.getStatus())) {
            throw new IllegalArgumentException("规则版本" + version.getVersionNo() + "已停用，不能用于新计划");
        }
        return version;
    }

    /**
     * 当前生效版本。
     */
    public WqRuleVersion currentActive() {
        List<WqRuleVersion> actives = versionMapper.selectList(
                new QueryWrapper<WqRuleVersion>().eq("status", WqRuleVersion.STATUS_ACTIVE)
                        .orderByDesc("id").last("LIMIT 1"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("尚无生效的水质指标规则版本，请先发布");
        }
        return actives.get(0);
    }

    public WqStageRule getStageOrThrow(Long versionId, String stageCode) {
        List<WqStageRule> stages = stageRuleMapper.selectList(
                new QueryWrapper<WqStageRule>().eq("version_id", versionId).eq("stage_code", stageCode));
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("规则版本下未配置该养殖阶段：" + stageCode);
        }
        return stages.get(0);
    }

    public List<WqStageRule> listStages(Long versionId) {
        return stageRuleMapper.selectList(
                new QueryWrapper<WqStageRule>().eq("version_id", versionId).orderByAsc("sort_no", "id"));
    }

    public List<WqIndicatorRule> listIndicators(Long stageRuleId) {
        return indicatorRuleMapper.selectList(
                new QueryWrapper<WqIndicatorRule>().eq("stage_rule_id", stageRuleId).orderByAsc("id"));
    }

    public List<WqIndicatorRule> listIndicatorsByVersionStage(Long versionId, String stageCode) {
        WqStageRule stage = getStageOrThrow(versionId, stageCode);
        return listIndicators(stage.getId());
    }

    private void validateRange(RuleVersionPublishRequest.StageDto stage, String versionNo) {
        for (RuleVersionPublishRequest.IndicatorDto ind : stage.getIndicators()) {
            if (ind.getMinValue() != null && ind.getMaxValue() != null
                    && ind.getMinValue().compareTo(ind.getMaxValue()) > 0) {
                throw new IllegalArgumentException(
                        "版本" + versionNo + "阶段" + stage.getStageCode()
                                + "指标" + ind.getIndicatorCode() + "的最小值不能大于最大值");
            }
        }
    }

    private WqRuleVersion copyOf(WqRuleVersion source) {
        WqRuleVersion copy = new WqRuleVersion();
        copy.setId(source.getId());
        copy.setVersionNo(source.getVersionNo());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
