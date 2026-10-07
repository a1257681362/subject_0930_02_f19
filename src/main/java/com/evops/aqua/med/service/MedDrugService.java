package com.evops.aqua.med.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.med.dto.MedDrugRequest;
import com.evops.aqua.med.entity.MedChangeEvidence;
import com.evops.aqua.med.entity.MedDrug;
import com.evops.aqua.med.mapper.MedDrugMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 药品档案：登记幂等（requestId 查重返回首条），编码全局唯一。
 */
@Service
public class MedDrugService {

    private final MedDrugMapper drugMapper;
    private final MedEvidenceService evidenceService;

    public MedDrugService(MedDrugMapper drugMapper, MedEvidenceService evidenceService) {
        this.drugMapper = drugMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MedDrug register(MedDrugRequest request) {
        MedDrug duplicated = drugMapper.selectOne(
                new QueryWrapper<MedDrug>().eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        Long codeUsed = drugMapper.selectCount(
                new QueryWrapper<MedDrug>().eq("drug_code", request.getDrugCode()));
        if (codeUsed != null && codeUsed > 0) {
            throw new IllegalArgumentException("药品编码已存在：" + request.getDrugCode());
        }
        MedDrug drug = new MedDrug();
        BeanUtils.copyProperties(request, drug);
        drug.setStatus(MedDrug.STATUS_ACTIVE);
        drugMapper.insert(drug);
        evidenceService.record(MedChangeEvidence.ENTITY_DRUG, drug.getId(),
                MedChangeEvidence.ACTION_DRUG_CREATE, request.getRequestId(),
                request.getOperator(), null, drug, "登记药品档案：" + drug.getDrugName());
        return drug;
    }

    public MedDrug getOrThrow(Long id) {
        MedDrug drug = drugMapper.selectById(id);
        if (drug == null) {
            throw new IllegalArgumentException("药品不存在，id=" + id);
        }
        return drug;
    }

    public void requireActive(MedDrug drug) {
        if (!MedDrug.STATUS_ACTIVE.equals(drug.getStatus())) {
            throw new IllegalArgumentException("药品" + drug.getDrugName() + "已停用，不能领用");
        }
    }

    public List<MedDrug> list() {
        return drugMapper.selectList(new QueryWrapper<MedDrug>().orderByAsc("id"));
    }
}
