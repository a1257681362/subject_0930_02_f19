package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.SensorRuleVersionPublishRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.mapper.SensorRuleVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 归并判定规则版本：盐度/溶氧/温度正常范围。
 * 规则调整只能发布新版本——旧版本自动停用，已归并读数与日报冻结旧版本结论不回改。
 */
@Service
public class SensorRuleVersionService {

    private final SensorRuleVersionMapper versionMapper;
    private final SensorEvidenceService evidenceService;

    public SensorRuleVersionService(SensorRuleVersionMapper versionMapper,
                                    SensorEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布新版本：版本号唯一；当前 ACTIVE 版本置 SUPERSEDED，新版本成为唯一生效版本。
     * 已归并的读数状态与日报结论保持冻结，不受新版本影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorRuleVersion publish(SensorRuleVersionPublishRequest request) {
        Long exists = versionMapper.selectCount(
                new QueryWrapper<SensorRuleVersion>().eq("version_no", request.getVersionNo()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("规则版本号已存在：" + request.getVersionNo());
        }
        validateRange("盐度", request.getSalinityMin(), request.getSalinityMax());
        validateRange("溶氧", request.getDoMin(), request.getDoMax());
        validateRange("温度", request.getTempMin(), request.getTempMax());

        SensorRuleVersion oldActive = findActive();
        if (oldActive != null) {
            SensorRuleVersion before = copyOf(oldActive);
            oldActive.setStatus(SensorRuleVersion.STATUS_SUPERSEDED);
            versionMapper.updateById(oldActive);
            evidenceService.record(SensorChangeEvidence.ENTITY_RULE_VERSION, oldActive.getId(),
                    SensorChangeEvidence.ACTION_SUPERSEDE, null, before, oldActive,
                    "规则版本[" + oldActive.getVersionNo() + "]停用，其归并结论保持冻结不回改");
        }

        SensorRuleVersion version = new SensorRuleVersion();
        version.setVersionNo(request.getVersionNo());
        version.setStatus(SensorRuleVersion.STATUS_ACTIVE);
        version.setSalinityMin(request.getSalinityMin());
        version.setSalinityMax(request.getSalinityMax());
        version.setDoMin(request.getDoMin());
        version.setDoMax(request.getDoMax());
        version.setTempMin(request.getTempMin());
        version.setTempMax(request.getTempMax());
        version.setRemark(request.getRemark());
        versionMapper.insert(version);
        evidenceService.record(SensorChangeEvidence.ENTITY_RULE_VERSION, version.getId(),
                SensorChangeEvidence.ACTION_PUBLISH, null, null, version,
                "发布规则版本[" + version.getVersionNo() + "]，仅作用于发布后的归并判定");
        return version;
    }

    public SensorRuleVersion findActive() {
        List<SensorRuleVersion> list = versionMapper.selectList(
                new QueryWrapper<SensorRuleVersion>().eq("status", SensorRuleVersion.STATUS_ACTIVE));
        return list.isEmpty() ? null : list.get(0);
    }

    public SensorRuleVersion requireActive() {
        SensorRuleVersion active = findActive();
        if (active == null) {
            throw new IllegalArgumentException("当前无生效的归并判定规则版本，请先发布规则版本");
        }
        return active;
    }

    public List<SensorRuleVersion> listAll() {
        return versionMapper.selectList(new QueryWrapper<SensorRuleVersion>().orderByAsc("id"));
    }

    private void validateRange(String name, java.math.BigDecimal min, java.math.BigDecimal max) {
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException(name + "正常范围下限不能大于上限：" + min + ">" + max);
        }
    }

    private SensorRuleVersion copyOf(SensorRuleVersion source) {
        SensorRuleVersion copy = new SensorRuleVersion();
        copy.setId(source.getId());
        copy.setVersionNo(source.getVersionNo());
        copy.setStatus(source.getStatus());
        copy.setSalinityMin(source.getSalinityMin());
        copy.setSalinityMax(source.getSalinityMax());
        copy.setDoMin(source.getDoMin());
        copy.setDoMax(source.getDoMax());
        copy.setTempMin(source.getTempMin());
        copy.setTempMax(source.getTempMax());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
