package com.evops.aqua.wq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.wq.entity.WqChangeEvidence;
import com.evops.aqua.wq.mapper.WqChangeEvidenceMapper;
import org.springframework.stereotype.Service;

/**
 * 变更证据：所有修改统一落 t_wq_change_evidence，只追加。
 */
@Service
public class WqEvidenceService {

    private final WqChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public WqEvidenceService(WqChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       Object before, Object after, String detail) {
        WqChangeEvidence evidence = new WqChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    public java.util.List<WqChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<WqChangeEvidence> wrapper = new QueryWrapper<>();
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
