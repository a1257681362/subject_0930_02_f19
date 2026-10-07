package com.evops.aqua.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.growth.dto.CoefficientSaveRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceCoefficient;
import com.evops.aqua.growth.mapper.GrowthDeviceCoefficientMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备校正系数：按"设备 + 版本"保存体重/体长校正系数。
 * 同设备旧 ACTIVE 版本在新版本保存时置 SUPERSEDED；已冻结在历史测量任务上的版本指针不变，
 * 因此发布新系数不会回头重算历史测量——历史不随新系数重算。
 */
@Service
public class GrowthCoefficientService {

    private final GrowthDeviceCoefficientMapper coeffMapper;
    private final GrowthEvidenceService evidenceService;

    public GrowthCoefficientService(GrowthDeviceCoefficientMapper coeffMapper,
                                    GrowthEvidenceService evidenceService) {
        this.coeffMapper = coeffMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 保存（发布）某设备的系数新版本。幂等：相同 requestId 重复请求返回首次版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthDeviceCoefficient save(CoefficientSaveRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<GrowthDeviceCoefficient> same = coeffMapper.selectList(
                    new QueryWrapper<GrowthDeviceCoefficient>().eq("request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }
        Long duplicated = coeffMapper.selectCount(new QueryWrapper<GrowthDeviceCoefficient>()
                .eq("device_code", request.getDeviceCode())
                .eq("version_no", request.getVersionNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException(
                    "设备" + request.getDeviceCode() + "的系数版本号已存在：" + request.getVersionNo());
        }

        GrowthDeviceCoefficient coeff = new GrowthDeviceCoefficient();
        coeff.setDeviceCode(request.getDeviceCode());
        coeff.setDeviceName(request.getDeviceName());
        coeff.setVersionNo(request.getVersionNo());
        coeff.setWeightCoeff(request.getWeightCoeff());
        coeff.setLengthCoeff(request.getLengthCoeff());
        coeff.setStatus(GrowthDeviceCoefficient.STATUS_ACTIVE);
        coeff.setEffectiveDate(request.getEffectiveDate());
        coeff.setRequestId(request.getRequestId());
        coeff.setRemark(request.getRemark());
        coeffMapper.insert(coeff);

        // 同设备旧 ACTIVE 版本停用（每设备同时刻只允许一个 ACTIVE）
        List<GrowthDeviceCoefficient> actives = coeffMapper.selectList(
                new QueryWrapper<GrowthDeviceCoefficient>()
                        .eq("device_code", request.getDeviceCode())
                        .eq("status", GrowthDeviceCoefficient.STATUS_ACTIVE)
                        .ne("id", coeff.getId()));
        for (GrowthDeviceCoefficient old : actives) {
            GrowthDeviceCoefficient before = copyOf(old);
            old.setStatus(GrowthDeviceCoefficient.STATUS_SUPERSEDED);
            coeffMapper.updateById(old);
            evidenceService.record(GrowthChangeEvidence.ENTITY_COEFFICIENT, old.getId(),
                    GrowthChangeEvidence.ACTION_UPDATE, request.getRequestId(), request.getOperator(),
                    before, old, "设备" + old.getDeviceCode() + "发布新版本" + coeff.getVersionNo()
                            + "，旧系数版本" + old.getVersionNo() + "停用（历史测量不重算）");
        }

        evidenceService.record(GrowthChangeEvidence.ENTITY_COEFFICIENT, coeff.getId(),
                GrowthChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getOperator(),
                null, coeff, "保存设备校正系数：设备=" + coeff.getDeviceCode()
                        + "，版本=" + coeff.getVersionNo()
                        + "，体重系数=" + coeff.getWeightCoeff()
                        + "，体长系数=" + coeff.getLengthCoeff());
        return coeff;
    }

    public GrowthDeviceCoefficient getOrThrow(Long id) {
        GrowthDeviceCoefficient coeff = coeffMapper.selectById(id);
        if (coeff == null) {
            throw new IllegalArgumentException("设备校正系数版本不存在，id=" + id);
        }
        return coeff;
    }

    /**
     * 某设备当前生效（ACTIVE）系数版本；没有则拒绝（须先为设备保存系数）。
     */
    public GrowthDeviceCoefficient currentActive(String deviceCode) {
        List<GrowthDeviceCoefficient> actives = coeffMapper.selectList(
                new QueryWrapper<GrowthDeviceCoefficient>()
                        .eq("device_code", deviceCode)
                        .eq("status", GrowthDeviceCoefficient.STATUS_ACTIVE)
                        .orderByDesc("id").last("LIMIT 1"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("设备[" + deviceCode + "]尚无生效校正系数版本，请先保存");
        }
        return actives.get(0);
    }

    /** 指定版本必须属于该设备且处于 ACTIVE，方可用于新方案。 */
    public GrowthDeviceCoefficient requireActiveForDevice(Long id, String deviceCode) {
        GrowthDeviceCoefficient coeff = getOrThrow(id);
        if (!coeff.getDeviceCode().equals(deviceCode)) {
            throw new IllegalArgumentException("系数版本" + id + "不属于设备[" + deviceCode + "]");
        }
        if (!GrowthDeviceCoefficient.STATUS_ACTIVE.equals(coeff.getStatus())) {
            throw new IllegalArgumentException(
                    "设备[" + deviceCode + "]的系数版本" + coeff.getVersionNo() + "已停用，不能用于新方案");
        }
        return coeff;
    }

    public List<GrowthDeviceCoefficient> list(String deviceCode) {
        QueryWrapper<GrowthDeviceCoefficient> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceCode != null && !deviceCode.trim().isEmpty(), "device_code", deviceCode);
        wrapper.orderByDesc("device_code", "id");
        return coeffMapper.selectList(wrapper);
    }

    private GrowthDeviceCoefficient copyOf(GrowthDeviceCoefficient source) {
        GrowthDeviceCoefficient copy = new GrowthDeviceCoefficient();
        copy.setId(source.getId());
        copy.setDeviceCode(source.getDeviceCode());
        copy.setDeviceName(source.getDeviceName());
        copy.setVersionNo(source.getVersionNo());
        copy.setWeightCoeff(source.getWeightCoeff());
        copy.setLengthCoeff(source.getLengthCoeff());
        copy.setStatus(source.getStatus());
        copy.setEffectiveDate(source.getEffectiveDate());
        copy.setRequestId(source.getRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
