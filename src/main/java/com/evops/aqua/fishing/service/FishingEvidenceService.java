package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.mapper.FishingChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 捕捞预约模块变更证据：所有写操作统一落 t_fishing_change_evidence，只追加。
 * 每条证据必须带操作者（operator）与幂等请求号，可按对象或 requestId 追溯。
 */
@Service
public class FishingEvidenceService {

    private final FishingChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public FishingEvidenceService(FishingChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        FishingChangeEvidence evidence = new FishingChangeEvidence();
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

    public List<FishingChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<FishingChangeEvidence> wrapper = new QueryWrapper<>();
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
