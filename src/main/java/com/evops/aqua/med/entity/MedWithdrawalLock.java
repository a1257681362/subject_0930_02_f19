package com.evops.aqua.med.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 池塘休药期出池锁定：每池一行。
 * lockedUntil 取该池在途（待审核/已审核）领用单各药品休药结束日的最晚值——
 * 不同药品叠加时取最晚结束日；提交/驳回/撤回时重算。
 */
@TableName("t_med_withdrawal_lock")
public class MedWithdrawalLock extends BaseEntity {

    private Long pondId;
    private LocalDate lockedUntil;
    private String detail;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public LocalDate getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDate lockedUntil) { this.lockedUntil = lockedUntil; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
