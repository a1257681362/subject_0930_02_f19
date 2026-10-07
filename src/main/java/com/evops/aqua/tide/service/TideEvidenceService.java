package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.mapper.TideChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 潮汐模块变更证据：所有修改统一落 t_tide_change_evidence，只追加。
 * 每条证据必须带操作者（operator）——任何写操作都不丢操作者。
 */
@Service
public class TideEvidenceService {

    private final TideChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public TideEvidenceService(TideChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        TideChangeEvidence evidence = new TideChangeEvidence();
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

    public List<TideChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<TideChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    /** 按动作+请求号查证据：用于改期等操作的幂等判定（同 requestId 不重复执行）。 */
    public TideChangeEvidence findByActionAndRequest(String action, String requestId) {
        return evidenceMapper.selectOne(new QueryWrapper<TideChangeEvidence>()
                .eq("action", action).eq("request_id", requestId).last("LIMIT 1"));
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
