package com.evops.aqua.med.service;

import com.evops.aqua.med.mapper.MedPondMutexMapper;
import org.springframework.stereotype.Component;

/**
 * 池塘级休药锁定互斥：同池的提交/审核/驳回/撤回/重提在此串行，
 * 杜绝跨池并发时两事务对 t_med_withdrawal_lock 的读-改-写互相覆盖
 * （曾表现为较早批次的休药期被套到另一批次、出池日提前）。
 * 必须在业务事务内调用，行锁在事务提交/回滚后由数据库自动释放。
 */
@Component
public class MedPondLockGuard {

    private final MedPondMutexMapper mutexMapper;

    public MedPondLockGuard(MedPondMutexMapper mutexMapper) {
        this.mutexMapper = mutexMapper;
    }

    /** 取得池塘互斥键并持有到当前事务结束。 */
    public void lock(Long pondId) {
        mutexMapper.ensureKey(pondId);
        mutexMapper.lockPond(pondId);
    }
}
