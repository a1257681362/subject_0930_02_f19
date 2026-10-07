package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.qual.dto.TempAuthGrantRequest;
import com.evops.aqua.qual.entity.QualPerson;
import com.evops.aqua.qual.entity.TempAuth;
import com.evops.aqua.qual.mapper.TempAuthMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 临时授权：必须带结束时间与审批单号；仅在 [effectiveFrom, effectiveTo) 内生效。
 * 到点自动失效由权限计算实时判定，扫描负责把过期授权置 EXPIRED 留证。
 */
@Service
public class TempAuthService {

    private final TempAuthMapper tempAuthMapper;
    private final PersonService personService;
    private final QualEvidenceService evidenceService;

    public TempAuthService(TempAuthMapper tempAuthMapper, PersonService personService,
                           QualEvidenceService evidenceService) {
        this.tempAuthMapper = tempAuthMapper;
        this.personService = personService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public TempAuth grant(TempAuthGrantRequest request) {
        TempAuth existed = findByRequestId(request.getRequestId());
        if (existed != null) {
            return existed;
        }
        QualPerson person = personService.getByCodeOrThrow(request.getPersonCode());
        if (!QualPerson.STATUS_ACTIVE.equals(person.getStatus())) {
            throw new IllegalArgumentException("人员[" + person.getPersonCode() + "]状态为 "
                    + person.getStatus() + "，不可授予临时权限");
        }
        LocalDateTime from = request.getEffectiveFrom() == null
                ? LocalDateTime.now() : request.getEffectiveFrom();
        LocalDateTime to = request.getEffectiveTo();
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("临时授权结束时间必须晚于生效时间");
        }
        String permCodes = normalizePermCodes(request.getPermCodes());
        TempAuth auth = new TempAuth();
        auth.setPersonId(person.getId());
        auth.setPermCodes(permCodes);
        auth.setEffectiveFrom(from);
        auth.setEffectiveTo(to);
        auth.setApprovalTicket(request.getApprovalTicket());
        auth.setReason(request.getReason());
        auth.setStatus(TempAuth.STATUS_ACTIVE);
        auth.setGrantedBy(request.getOperator());
        auth.setRequestId(request.getRequestId());
        tempAuthMapper.insert(auth);
        evidenceService.record("TEMP_AUTH", auth.getId(), "TEMP_AUTH_GRANT", request.getRequestId(),
                request.getOperator(), null, auth,
                "人员[" + person.getPersonCode() + "]临时授权，审批单 " + auth.getApprovalTicket()
                        + "，有效期 [" + from + ", " + to + ")");
        return auth;
    }

    public TempAuth findByRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        return tempAuthMapper.selectOne(new QueryWrapper<TempAuth>().eq("request_id", requestId));
    }

    public List<TempAuth> listByPerson(Long personId) {
        return tempAuthMapper.selectList(new QueryWrapper<TempAuth>()
                .eq("person_id", personId).orderByAsc("id"));
    }

    /** 规范化权限码：去重去空，每个必须是 menu: 或 api: 开头。 */
    static String normalizePermCodes(String permCodes) {
        List<String> codes = RoleScopeService.splitCodes(permCodes);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("临时权限码不能为空");
        }
        for (String code : codes) {
            if (!code.startsWith("menu:") && !code.startsWith("api:")) {
                throw new IllegalArgumentException("权限码必须以 menu: 或 api: 开头：" + code);
            }
        }
        return String.join(",", codes);
    }
}
