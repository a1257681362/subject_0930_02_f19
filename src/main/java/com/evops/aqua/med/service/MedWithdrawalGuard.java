package com.evops.aqua.med.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.med.entity.MedWithdrawalLock;
import com.evops.aqua.med.mapper.MedWithdrawalLockMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 休药期守卫：供台账（出池）调用的只读校验。
 * 只依赖本模块 Mapper，不反向依赖台账 Service，避免循环依赖。
 * 池塘存在休药期锁定时，出池日期早于 lockedUntil 一律拒绝。
 */
@Component
public class MedWithdrawalGuard {

    private final MedWithdrawalLockMapper lockMapper;

    public MedWithdrawalGuard(MedWithdrawalLockMapper lockMapper) {
        this.lockMapper = lockMapper;
    }

    /** 池塘休药期未结束则拒绝出池：预计出池日不早于 lockedUntil。 */
    public void requireHarvestAllowed(Long pondId, LocalDate harvestDate) {
        if (harvestDate == null) {
            return;
        }
        MedWithdrawalLock lock = lockOfPond(pondId);
        if (lock != null && harvestDate.isBefore(lock.getLockedUntil())) {
            throw new IllegalArgumentException("池塘id=" + pondId + "休药期未结束（"
                    + lock.getDetail() + "），预计出池日不早于" + lock.getLockedUntil() + "，禁止出池");
        }
    }

    public MedWithdrawalLock lockOfPond(Long pondId) {
        return lockMapper.selectOne(new QueryWrapper<MedWithdrawalLock>()
                .eq("pond_id", pondId)
                .last("limit 1"));
    }
}
