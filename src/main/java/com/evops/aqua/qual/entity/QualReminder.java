package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 证书到期提醒：uk(证书, 提醒日期) 保证重扫/重启不重复提醒。
 */
@TableName("t_qual_reminder")
public class QualReminder extends BaseEntity {

    private Long certificateId;
    private LocalDate remindDate;
    private Integer daysLeft;
    private String certNo;
    private Long personId;

    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
    public LocalDate getRemindDate() { return remindDate; }
    public void setRemindDate(LocalDate remindDate) { this.remindDate = remindDate; }
    public Integer getDaysLeft() { return daysLeft; }
    public void setDaysLeft(Integer daysLeft) { this.daysLeft = daysLeft; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
}
