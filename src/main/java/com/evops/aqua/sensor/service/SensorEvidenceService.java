package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.mapper.SensorChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 变更证据：传感器接入模块所有修改统一落 t_sensor_change_evidence，只追加。
 * 高风险观察拒绝等场景使用 REQUIRES_NEW 独立事务留证：业务事务回滚证据仍在，
 * 且任何异常路径只新增证据行，绝不更新/覆盖已有凭证。
 */
@Service
public class SensorEvidenceService {

    private final SensorChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public SensorEvidenceService(SensorChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       Object before, Object after, String detail) {
        SensorChangeEvidence evidence = new SensorChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    /** 独立事务留证：外层业务事务回滚（如高风险观察拒绝归并）时证据仍必须留下。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordStandalone(String entityType, Long entityId, String action, String requestId,
                                 Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, before, after, detail);
    }

    public List<SensorChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<SensorChangeEvidence> wrapper = new QueryWrapper<>();
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
