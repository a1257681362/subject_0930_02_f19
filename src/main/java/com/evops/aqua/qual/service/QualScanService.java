package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.qual.entity.QualCertificate;
import com.evops.aqua.qual.entity.QualReminder;
import com.evops.aqua.qual.entity.TempAuth;
import com.evops.aqua.qual.mapper.QualCertificateMapper;
import com.evops.aqua.qual.mapper.QualReminderMapper;
import com.evops.aqua.qual.mapper.TempAuthMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资质扫描（Quartz 每日 + 手工触发同一逻辑）：
 * 1) 到期前提醒——有效证书到期日落在 [今天, 今天+提醒天数] 的逐证逐日生成提醒，uk 保证重扫不重复；
 * 2) 过期证书置 EXPIRED 并留证（权限收回不等待本扫描，由权限计算实时判定）；
 * 3) 过期临时授权置 EXPIRED 并留证。
 */
@Service
public class QualScanService {

    public static final String OPERATOR = "system-scheduler";

    private final QualCertificateMapper certificateMapper;
    private final QualReminderMapper reminderMapper;
    private final TempAuthMapper tempAuthMapper;
    private final QualEvidenceService evidenceService;
    private final int remindDays;

    public QualScanService(QualCertificateMapper certificateMapper, QualReminderMapper reminderMapper,
                           TempAuthMapper tempAuthMapper, QualEvidenceService evidenceService,
                           @Value("${aqua.qual.remind-days:30}") int remindDays) {
        this.certificateMapper = certificateMapper;
        this.reminderMapper = reminderMapper;
        this.tempAuthMapper = tempAuthMapper;
        this.evidenceService = evidenceService;
        this.remindDays = remindDays;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanNow() {
        LocalDate today = LocalDate.now();
        int reminded = scanReminders(today);
        int expiredCerts = expireCertificates(today);
        int expiredAuths = expireTempAuths();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reminded", reminded);
        summary.put("expiredCertificates", expiredCerts);
        summary.put("expiredTempAuths", expiredAuths);
        summary.put("remindDays", remindDays);
        return summary;
    }

    private int scanReminders(LocalDate today) {
        List<QualCertificate> expiring = certificateMapper.selectList(new QueryWrapper<QualCertificate>()
                .eq("status", QualCertificate.STATUS_VALID)
                .ge("expires_at", today)
                .le("expires_at", today.plusDays(remindDays))
                .orderByAsc("id"));
        int count = 0;
        for (QualCertificate cert : expiring) {
            QualReminder reminder = new QualReminder();
            reminder.setCertificateId(cert.getId());
            reminder.setRemindDate(today);
            reminder.setDaysLeft((int) ChronoUnit.DAYS.between(today, cert.getExpiresAt()));
            reminder.setCertNo(cert.getCertNo());
            reminder.setPersonId(cert.getPersonId());
            try {
                reminderMapper.insert(reminder);
                count++;
            } catch (DuplicateKeyException duplicate) {
                // uk(证书, 提醒日期)：重扫/重启不重复提醒
            }
        }
        return count;
    }

    private int expireCertificates(LocalDate today) {
        List<QualCertificate> expired = certificateMapper.selectList(new QueryWrapper<QualCertificate>()
                .eq("status", QualCertificate.STATUS_VALID)
                .lt("expires_at", today)
                .orderByAsc("id"));
        for (QualCertificate cert : expired) {
            cert.setStatus(QualCertificate.STATUS_EXPIRED);
            certificateMapper.updateById(cert);
            evidenceService.record("CERTIFICATE", cert.getId(), "CERT_EXPIRED", null, OPERATOR,
                    "status=VALID", cert,
                    "证书 " + cert.getCertNo() + " 已于 " + cert.getExpiresAt()
                            + " 到期，对应菜单与接口权限已收回");
        }
        return expired.size();
    }

    private int expireTempAuths() {
        List<TempAuth> expired = tempAuthMapper.selectList(new QueryWrapper<TempAuth>()
                .eq("status", TempAuth.STATUS_ACTIVE)
                .le("effective_to", LocalDateTime.now())
                .orderByAsc("id"));
        for (TempAuth auth : expired) {
            auth.setStatus(TempAuth.STATUS_EXPIRED);
            tempAuthMapper.updateById(auth);
            evidenceService.record("TEMP_AUTH", auth.getId(), "TEMP_AUTH_EXPIRED", null, OPERATOR,
                    "status=ACTIVE", auth,
                    "临时授权（审批单 " + auth.getApprovalTicket() + "）已于 "
                            + auth.getEffectiveTo() + " 到期，权限自动收回");
        }
        return expired.size();
    }

    public List<QualReminder> listReminders(Long personId) {
        QueryWrapper<QualReminder> wrapper = new QueryWrapper<>();
        wrapper.eq(personId != null, "person_id", personId);
        wrapper.orderByAsc("id");
        return reminderMapper.selectList(wrapper);
    }
}
