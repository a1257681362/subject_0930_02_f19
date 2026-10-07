package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.entity.BizLock;
import com.evops.aqua.maint.entity.ConcurrentAudit;
import com.evops.aqua.maint.mapper.BizLockMapper;
import com.evops.aqua.maint.mapper.ConcurrentAuditMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 海水养殖管理同一操作并发裁决护栏。
 *
 * 裁决口径（谁生效、谁被拒绝）：
 * 1. 同一业务键（bizKey，如 设备:停机恢复 / 工单:开工）+ 同一动作的请求，先到先得：
 *    先拿到 t_maint_biz_lock 唯一键的请求 EFFECTIVE 生效，正常执行；
 * 2. 其余并发请求拿锁失败 → 立即 CONCURRENT_REJECTED 被拒绝，响应中说明生效请求号与生效操作者；
 * 3. 每个请求（生效与被拒）都在 t_maint_concurrent_audit 留一行审计：业务键、动作、结论、
 *    双方 requestId/操作者、拒绝原因。被拒审计以独立事务落库，不随外层回滚；
 * 4. 持锁事务结束后锁行删除；同一 requestId 之后重放按业务幂等返回首次结果（不算并发拒绝）。
 */
@Service
public class ConcurrencyGuard {

    private final BizLockMapper lockMapper;
    private final ConcurrentAuditMapper auditMapper;
    /** 自代理：拒绝审计必须在独立事务提交，不能随本方法抛异常回滚，故经代理调用 REQUIRES_NEW 方法。 */
    private final ConcurrencyGuard self;

    public ConcurrencyGuard(BizLockMapper lockMapper,
                            ConcurrentAuditMapper auditMapper,
                            @Lazy ConcurrencyGuard self) {
        this.lockMapper = lockMapper;
        this.auditMapper = auditMapper;
        this.self = self;
    }

    /**
     * 尝试取得业务键执行权。锁行以独立事务立即提交，对所有并发请求立即可见——
     * 先到者持锁执行，后到者立刻看到生效者并被拒绝。
     *
     * @return 生效凭据（同 requestId 重入也算生效，由业务层自行保证幂等）
     * @throws ConcurrentRejectedException 并发被拒，审计已独立事务留痕
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public LeasedLock tryAcquire(String bizKey, String action, String requestId, String operator) {
        BizLock existing = lockMapper.selectOne(new QueryWrapper<BizLock>().eq("lock_key", bizKey));
        if (existing != null) {
            if (existing.getRequestId().equals(requestId)) {
                // 同请求重入：生效凭据回放，不重复审计
                return new LeasedLock(bizKey, requestId, operator, false);
            }
            self.rejectStandalone(bizKey, action, requestId, operator,
                    existing.getRequestId(), existing.getOperator(),
                    "同一操作正在处理中：生效请求=" + existing.getRequestId()
                            + "（操作者" + existing.getOperator() + "），本请求=" + requestId
                            + "（操作者" + operator + "）并发被拒");
            throw new ConcurrentRejectedException("同一操作已在处理中：请求" + requestId
                    + "（操作者" + operator + "）被拒绝，请求" + existing.getRequestId()
                    + "（操作者" + existing.getOperator() + "）先生效");
        }
        BizLock lock = new BizLock();
        lock.setLockKey(bizKey);
        lock.setRequestId(requestId);
        lock.setOperator(operator);
        lock.setLockedAt(LocalDateTime.now());
        try {
            lockMapper.insert(lock);
        } catch (DuplicateKeyException dup) {
            // 并发窗口极小：唯一约束兜底，再查一次拿到生效者信息
            BizLock winner = lockMapper.selectOne(new QueryWrapper<BizLock>().eq("lock_key", bizKey));
            String winnerRequest = winner == null ? requestId : winner.getRequestId();
            String winnerOperator = winner == null ? operator : winner.getOperator();
            self.rejectStandalone(bizKey, action, requestId, operator, winnerRequest, winnerOperator,
                    "同一操作并发竞争唯一执行权失败：生效请求=" + winnerRequest + "，本请求=" + requestId + "被拒");
            throw new ConcurrentRejectedException("同一操作并发冲突：请求" + requestId + "（操作者" + operator
                    + "）被拒绝，请求" + winnerRequest + "（操作者" + winnerOperator + "）先生效");
        }
        return new LeasedLock(bizKey, requestId, operator, true);
    }

    /**
     * 生效请求落审计行（业务事务内，随业务一起提交——业务失败回滚则该请求未生效）。
     */
    public void recordEffective(LeasedLock lease, String action) {
        if (!lease.isFresh()) {
            return;
        }
        ConcurrentAudit audit = new ConcurrentAudit();
        audit.setBizKey(lease.getBizKey());
        audit.setAction(action);
        audit.setVerdict(ConcurrentAudit.VERDICT_EFFECTIVE);
        audit.setRequestId(lease.getRequestId());
        audit.setOperator(lease.getOperator());
        audit.setEffectiveRequestId(lease.getRequestId());
        audit.setEffectiveOperator(lease.getOperator());
        auditMapper.insert(audit);
    }

    /**
     * 被拒审计独立事务落库：即使外层业务回滚，"谁被谁拒绝"的事实也必须留下。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void rejectStandalone(String bizKey, String action, String requestId, String operator,
                                 String effectiveRequestId, String effectiveOperator, String reason) {
        ConcurrentAudit existing = auditMapper.selectOne(
                new QueryWrapper<ConcurrentAudit>().eq("request_id", requestId));
        if (existing != null) {
            return; // 审计只追加首次结论，同请求重放不覆盖
        }
        ConcurrentAudit audit = new ConcurrentAudit();
        audit.setBizKey(bizKey);
        audit.setAction(action);
        audit.setVerdict(ConcurrentAudit.VERDICT_REJECTED);
        audit.setRequestId(requestId);
        audit.setOperator(operator);
        audit.setEffectiveRequestId(effectiveRequestId);
        audit.setEffectiveOperator(effectiveOperator);
        audit.setRejectReason(reason);
        auditMapper.insert(audit);
    }

    /**
     * 释放业务键执行权：注册到当前业务事务结束后（提交或回滚）以独立事务删除锁行。
     * 这样在业务数据真正落库前，锁对其他并发请求始终可见——不会出现"业务未提交但锁已释放"的空窗。
     * 无事务上下文时立即释放；同 requestId 重入的非新锁不删除。
     */
    public void release(LeasedLock lease) {
        if (lease == null || !lease.isFresh()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    deleteLock(lease.getBizKey());
                }
            });
        } else {
            deleteLock(lease.getBizKey());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteLock(String lockKey) {
        lockMapper.delete(new QueryWrapper<BizLock>().eq("lock_key", lockKey));
    }

    public java.util.List<ConcurrentAudit> listAudit(String bizKey, String requestId) {
        QueryWrapper<ConcurrentAudit> wrapper = new QueryWrapper<>();
        wrapper.eq(bizKey != null && !bizKey.trim().isEmpty(), "biz_key", bizKey);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.orderByAsc("id");
        return auditMapper.selectList(wrapper);
    }

    /**
     * 生效凭据。fresh=true 表示本次新拿到执行权（需记生效审计）；false 表示同请求重入。
     * 锁行由持锁事务结束自动释放（回滚/提交后不存在），无需显式释放。
     */
    public static class LeasedLock {
        private final String bizKey;
        private final String requestId;
        private final String operator;
        private final boolean fresh;

        public LeasedLock(String bizKey, String requestId, String operator, boolean fresh) {
            this.bizKey = bizKey;
            this.requestId = requestId;
            this.operator = operator;
            this.fresh = fresh;
        }

        public String getBizKey() { return bizKey; }
        public String getRequestId() { return requestId; }
        public String getOperator() { return operator; }
        public boolean isFresh() { return fresh; }
    }
}
