package com.evops.aqua.release.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.mapper.ReleaseChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 出池检验变更证据：所有状态变化统一落 t_rel_change_evidence，只追加。
 */
@Service
public class ReleaseEvidenceService {

    private final ReleaseChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public ReleaseEvidenceService(ReleaseChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        ReleaseChangeEvidence evidence = new ReleaseChangeEvidence();
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

    public List<ReleaseChangeEvidence> list(String entityType, Long entityId, String requestId, String action) {
        QueryWrapper<ReleaseChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.eq(action != null && !action.trim().isEmpty(), "action", action);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    /** 某（对象类型 + 幂等请求号 + 动作）是否已留证：用于接口幂等判定。 */
    public boolean exists(String entityType, String requestId, String action) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        Long count = evidenceMapper.selectCount(new QueryWrapper<ReleaseChangeEvidence>()
                .eq("entity_type", entityType)
                .eq("request_id", requestId)
                .eq("action", action));
        return count != null && count > 0;
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

    /** 当前登录操作者，未登录场景（定时/测试直调）回落 SYSTEM。 */
    public static String currentOperator() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            return principal == null ? "SYSTEM" : String.valueOf(principal);
        } catch (Exception ex) {
            return "SYSTEM";
        }
    }
}
