package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.qual.entity.QualChangeEvidence;
import com.evops.aqua.qual.mapper.QualChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 人员资质模块变更证据：所有修改统一落 t_qual_change_evidence，只追加。
 * 每条证据必须带操作者——包括定时扫描在内的任何写操作都不丢操作者。
 */
@Service
public class QualEvidenceService {

    private final QualChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public QualEvidenceService(QualChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        QualChangeEvidence evidence = new QualChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setOperator(operator);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    /** 独立事务写证据：业务回滚但审计/证据必须留下的场景。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordStandalone(String entityType, Long entityId, String action, String requestId,
                                 String operator, Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, operator, before, after, detail);
    }

    public List<QualChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<QualChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
