package com.evops.aqua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.mapper.ReportChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 监管月报模块变更证据：口径发布/停用、快照固化、任务失败重试、缺权拒绝、导出均落
 * t_report_change_evidence，只追加。任务失败/入口拒绝使用独立事务留证，不随业务事务回滚。
 */
@Service
public class ReportEvidenceService {

    public static final String OPERATOR_SCHEDULER = "system-scheduler";

    private final ReportChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public ReportEvidenceService(ReportChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        ReportChangeEvidence evidence = new ReportChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId == null ? 0L : entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setOperator(operator);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    /** 独立事务留证：业务事务回滚（任务失败/入口拒绝）时证据仍必须留下。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordStandalone(String entityType, Long entityId, String action, String requestId,
                                 String operator, Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, operator, before, after, detail);
    }

    public List<ReportChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<ReportChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    /** 当前登录操作者，未登录（Quartz/测试直调）回落 SYSTEM。 */
    public static String currentOperator() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            return principal == null ? "SYSTEM" : String.valueOf(principal);
        } catch (Exception ex) {
            return "SYSTEM";
        }
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
