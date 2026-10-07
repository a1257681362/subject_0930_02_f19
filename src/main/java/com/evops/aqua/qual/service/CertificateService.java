package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.qual.dto.CertificateRegisterRequest;
import com.evops.aqua.qual.entity.QualCertificate;
import com.evops.aqua.qual.entity.QualPerson;
import com.evops.aqua.qual.mapper.QualCertificateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资质证书：登记幂等、吊销留证。
 * 有效性按 expires_at 与当前日期实时判定——过期当刻权限即收回，不等待扫描置 EXPIRED。
 */
@Service
public class CertificateService {

    private final QualCertificateMapper certificateMapper;
    private final PersonService personService;
    private final QualEvidenceService evidenceService;

    public CertificateService(QualCertificateMapper certificateMapper, PersonService personService,
                              QualEvidenceService evidenceService) {
        this.certificateMapper = certificateMapper;
        this.personService = personService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public QualCertificate register(CertificateRegisterRequest request) {
        QualCertificate existed = findByRequestId(request.getRequestId());
        if (existed != null) {
            return existed;
        }
        QualPerson person = personService.getByCodeOrThrow(request.getPersonCode());
        Long duplicated = certificateMapper.selectCount(
                new QueryWrapper<QualCertificate>().eq("cert_no", request.getCertNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("证书编号已存在：" + request.getCertNo());
        }
        if (request.getIssuedAt() != null && request.getIssuedAt().isAfter(request.getExpiresAt())) {
            throw new IllegalArgumentException("发证日期不能晚于到期日");
        }
        QualCertificate cert = new QualCertificate();
        cert.setPersonId(person.getId());
        cert.setCertNo(request.getCertNo());
        cert.setCertType(request.getCertType().trim().toUpperCase());
        cert.setIssuedAt(request.getIssuedAt());
        cert.setExpiresAt(request.getExpiresAt());
        cert.setStatus(QualCertificate.STATUS_VALID);
        cert.setRequestId(request.getRequestId());
        certificateMapper.insert(cert);
        evidenceService.record("CERTIFICATE", cert.getId(), "CERT_REGISTER", request.getRequestId(),
                request.getOperator(), null, cert,
                "人员[" + person.getPersonCode() + "]登记证书 " + cert.getCertNo());
        return cert;
    }

    @Transactional(rollbackFor = Exception.class)
    public QualCertificate revoke(Long id, String operator, String reason) {
        QualCertificate cert = getOrThrow(id);
        if (QualCertificate.STATUS_REVOKED.equals(cert.getStatus())) {
            return cert;
        }
        if (QualCertificate.STATUS_EXPIRED.equals(cert.getStatus())) {
            throw new IllegalArgumentException("证书已过期，无需吊销：" + cert.getCertNo());
        }
        if (operator == null || operator.trim().isEmpty() || reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("吊销证书必须登记操作者与原因");
        }
        String before = "status=" + cert.getStatus();
        cert.setStatus(QualCertificate.STATUS_REVOKED);
        cert.setRevokedBy(operator);
        cert.setRevokedAt(LocalDateTime.now());
        cert.setRevokeReason(reason);
        certificateMapper.updateById(cert);
        evidenceService.record("CERTIFICATE", cert.getId(), "CERT_REVOKE", null, operator,
                before, cert, "吊销证书 " + cert.getCertNo() + "，原因：" + reason);
        return cert;
    }

    public QualCertificate getOrThrow(Long id) {
        QualCertificate cert = certificateMapper.selectById(id);
        if (cert == null) {
            throw new IllegalArgumentException("证书不存在，id=" + id);
        }
        return cert;
    }

    public QualCertificate findByRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        return certificateMapper.selectOne(
                new QueryWrapper<QualCertificate>().eq("request_id", requestId));
    }

    public List<QualCertificate> listByPerson(Long personId) {
        return certificateMapper.selectList(new QueryWrapper<QualCertificate>()
                .eq("person_id", personId).orderByAsc("id"));
    }

    /**
     * 岗位所需证书当前是否有效：实时按到期日判定（expires_at >= 今天 且状态 VALID）。
     * 证书类型为空表示该岗位不要求证书。
     */
    public boolean hasValidRequiredCert(Long personId, String requiredCertType) {
        if (requiredCertType == null || requiredCertType.trim().isEmpty()) {
            return true;
        }
        Long count = certificateMapper.selectCount(new QueryWrapper<QualCertificate>()
                .eq("person_id", personId)
                .eq("cert_type", requiredCertType)
                .eq("status", QualCertificate.STATUS_VALID)
                .ge("expires_at", LocalDate.now()));
        return count != null && count > 0;
    }

    public long countByPerson(Long personId, boolean validOnly) {
        QueryWrapper<QualCertificate> wrapper = new QueryWrapper<QualCertificate>()
                .eq("person_id", personId);
        if (validOnly) {
            wrapper.eq("status", QualCertificate.STATUS_VALID)
                    .ge("expires_at", LocalDate.now());
        }
        Long count = certificateMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }
}
