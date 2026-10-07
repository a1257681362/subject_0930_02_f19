package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.qual.dto.PersonRegisterRequest;
import com.evops.aqua.qual.dto.RoleChangeRequest;
import com.evops.aqua.qual.entity.QualCertificate;
import com.evops.aqua.qual.entity.QualPerson;
import com.evops.aqua.qual.entity.RoleChange;
import com.evops.aqua.qual.entity.RoleScope;
import com.evops.aqua.qual.mapper.QualCertificateMapper;
import com.evops.aqua.qual.mapper.QualPersonMapper;
import com.evops.aqua.qual.mapper.RoleChangeMapper;
import com.evops.aqua.qual.mapper.TempAuthMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖人员档案：登记幂等、岗位变更只追加历史（历史操作不删除）、解冻恢复。
 * 列表 / 详情 / CSV 导出共用同一 loadRows 行源，保证三个出口口径一致。
 */
@Service
public class PersonService {

    private final QualPersonMapper personMapper;
    private final QualCertificateMapper certificateMapper;
    private final TempAuthMapper tempAuthMapper;
    private final RoleChangeMapper roleChangeMapper;
    private final RoleScopeService roleScopeService;
    private final QualEvidenceService evidenceService;

    public PersonService(QualPersonMapper personMapper, QualCertificateMapper certificateMapper,
                         TempAuthMapper tempAuthMapper, RoleChangeMapper roleChangeMapper,
                         RoleScopeService roleScopeService, QualEvidenceService evidenceService) {
        this.personMapper = personMapper;
        this.certificateMapper = certificateMapper;
        this.tempAuthMapper = tempAuthMapper;
        this.roleChangeMapper = roleChangeMapper;
        this.roleScopeService = roleScopeService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public QualPerson register(PersonRegisterRequest request) {
        QualPerson existed = findByRequestId(request.getRequestId());
        if (existed != null) {
            return existed;
        }
        roleScopeService.requireRole(request.getRoleCode());
        Long duplicated = personMapper.selectCount(
                new QueryWrapper<QualPerson>().eq("person_code", request.getPersonCode()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("人员编号已存在：" + request.getPersonCode());
        }
        QualPerson person = new QualPerson();
        person.setPersonCode(request.getPersonCode());
        person.setPersonName(request.getPersonName());
        person.setRoleCode(request.getRoleCode().trim().toUpperCase());
        person.setStatus(QualPerson.STATUS_ACTIVE);
        person.setPhone(request.getPhone());
        person.setRemark(request.getRemark());
        person.setRequestId(request.getRequestId());
        personMapper.insert(person);
        evidenceService.record("PERSON", person.getId(), "PERSON_REGISTER", request.getRequestId(),
                "system", null, person,
                "登记人员[" + person.getPersonCode() + "]" + person.getPersonName()
                        + "，岗位 " + person.getRoleCode());
        return person;
    }

    /**
     * 岗位变更：只追加 t_qual_role_change 历史行并改当前岗位，
     * 历史证书、临时授权、变更证据一律保留不删。
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleChange changeRole(Long personId, RoleChangeRequest request) {
        RoleChange existed = roleChangeMapper.selectOne(
                new QueryWrapper<RoleChange>().eq("request_id", request.getRequestId()));
        if (existed != null) {
            return existed;
        }
        QualPerson person = getOrThrow(personId);
        roleScopeService.requireRole(request.getToRole());
        String toRole = request.getToRole().trim().toUpperCase();
        if (toRole.equals(person.getRoleCode())) {
            throw new IllegalArgumentException("目标岗位与当前岗位相同：" + toRole);
        }
        String before = "role=" + person.getRoleCode();
        RoleChange change = new RoleChange();
        change.setPersonId(person.getId());
        change.setFromRole(person.getRoleCode());
        change.setToRole(toRole);
        change.setReason(request.getReason());
        change.setOperator(request.getOperator());
        change.setChangedAt(LocalDateTime.now());
        change.setRequestId(request.getRequestId());
        roleChangeMapper.insert(change);
        person.setRoleCode(toRole);
        personMapper.updateById(person);
        evidenceService.record("PERSON", person.getId(), "ROLE_CHANGE", request.getRequestId(),
                request.getOperator(), before, "role=" + toRole,
                "岗位变更 " + change.getFromRole() + " → " + toRole + "，原因：" + request.getReason()
                        + "；历史操作记录保留不删");
        return change;
    }

    /** 解冻：恢复在职，权限按当前证书/范围重新生效。 */
    @Transactional(rollbackFor = Exception.class)
    public QualPerson unfreeze(Long personId, String operator) {
        QualPerson person = getOrThrow(personId);
        if (QualPerson.STATUS_ACTIVE.equals(person.getStatus())) {
            return person;
        }
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("解冻必须登记操作者");
        }
        String before = "status=" + person.getStatus();
        person.setStatus(QualPerson.STATUS_ACTIVE);
        personMapper.updateById(person);
        evidenceService.record("PERSON", person.getId(), "PERSON_UNFREEZE", null, operator,
                before, "status=ACTIVE", "人员[" + person.getPersonCode() + "]解冻恢复在职");
        return person;
    }

    public QualPerson getOrThrow(Long id) {
        QualPerson person = personMapper.selectById(id);
        if (person == null) {
            throw new IllegalArgumentException("人员不存在，id=" + id);
        }
        return person;
    }

    public QualPerson getByCodeOrThrow(String personCode) {
        QualPerson person = personMapper.selectOne(
                new QueryWrapper<QualPerson>().eq("person_code", personCode));
        if (person == null) {
            throw new IllegalArgumentException("人员不存在，编号=" + personCode);
        }
        return person;
    }

    public QualPerson findByRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        return personMapper.selectOne(new QueryWrapper<QualPerson>().eq("request_id", requestId));
    }

    public List<RoleChange> listRoleChanges(Long personId) {
        return roleChangeMapper.selectList(new QueryWrapper<RoleChange>()
                .eq("person_id", personId).orderByAsc("id"));
    }

    /**
     * 列表 / 详情 / 导出共用行源：人员基础字段 + 证书统计（有效证书按当前日期实时判定）。
     */
    public List<Map<String, Object>> loadRows(String role, String status, String keyword) {
        QueryWrapper<QualPerson> wrapper = new QueryWrapper<>();
        wrapper.eq(role != null && !role.trim().isEmpty(), "role_code",
                role == null ? null : role.trim().toUpperCase());
        wrapper.eq(status != null && !status.trim().isEmpty(), "status",
                status == null ? null : status.trim().toUpperCase());
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like("person_code", keyword.trim())
                    .or().like("person_name", keyword.trim()));
        }
        wrapper.orderByAsc("id");
        List<QualPerson> persons = personMapper.selectList(wrapper);
        Map<String, String> roleNames = new LinkedHashMap<>();
        for (RoleScope scope : roleScopeService.listAll()) {
            roleNames.put(scope.getRoleCode(), scope.getRoleName());
        }
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QualPerson person : persons) {
            Long certTotal = certificateMapper.selectCount(new QueryWrapper<QualCertificate>()
                    .eq("person_id", person.getId()));
            Long certValid = certificateMapper.selectCount(new QueryWrapper<QualCertificate>()
                    .eq("person_id", person.getId())
                    .eq("status", QualCertificate.STATUS_VALID)
                    .ge("expires_at", today));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", person.getId());
            row.put("personCode", person.getPersonCode());
            row.put("personName", person.getPersonName());
            row.put("roleCode", person.getRoleCode());
            row.put("roleName", roleNames.getOrDefault(person.getRoleCode(), person.getRoleCode()));
            row.put("status", person.getStatus());
            row.put("phone", person.getPhone());
            row.put("certTotal", certTotal == null ? 0 : certTotal);
            row.put("certValid", certValid == null ? 0 : certValid);
            row.put("createTime", person.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    /** CSV 导出：与列表同一 loadRows 行源，口径一致。 */
    public String exportCsv(String role, String status, String keyword) {
        List<Map<String, Object>> rows = loadRows(role, status, keyword);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("人员编号,姓名,岗位,岗位名称,状态,电话,有效证书数,证书总数,创建时间\r\n");
        for (Map<String, Object> row : rows) {
            List<String> cols = new ArrayList<>();
            cols.add(csv(row.get("personCode")));
            cols.add(csv(row.get("personName")));
            cols.add(csv(row.get("roleCode")));
            cols.add(csv(row.get("roleName")));
            cols.add(csv(row.get("status")));
            cols.add(csv(row.get("phone")));
            cols.add(csv(row.get("certValid")));
            cols.add(csv(row.get("certTotal")));
            cols.add(csv(row.get("createTime")));
            sb.append(String.join(",", cols)).append("\r\n");
        }
        return sb.toString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
