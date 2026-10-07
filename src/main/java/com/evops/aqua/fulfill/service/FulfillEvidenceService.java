package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.mapper.FulfillChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 履约模块变更证据：所有写操作统一落 t_fulfill_change_evidence，只追加。
 * 每条证据必须带操作者（operator）；外部同步失败用独立事务留证，不随业务回滚。
 */
@Service
public class FulfillEvidenceService {

    private final FulfillChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public FulfillEvidenceService(FulfillChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        FulfillChangeEvidence evidence = new FulfillChangeEvidence();
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

    /**
     * 独立事务写证据：外部同步失败等场景下业务事务可能回滚，但失败事实必须留下。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordStandalone(String entityType, Long entityId, String action, String requestId,
                                 String operator, Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, operator, before, after, detail);
    }

    public List<FulfillChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<FulfillChangeEvidence> wrapper = new QueryWrapper<>();
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
