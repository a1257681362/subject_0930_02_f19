package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 业务键并发锁：同一操作并发发起时库级串行化的凭据。唯一约束 uk(lock_key) 保证同键只有一个生效者。
 * 持锁事务提交/回滚后行删除；后续请求按 requestId 幂等回放首次结果。
 */
@TableName("t_maint_biz_lock")
public class BizLock extends BaseEntity {

    private String lockKey;
    private String requestId;
    private String operator;
    private LocalDateTime lockedAt;

    public String getLockKey() { return lockKey; }
    public void setLockKey(String lockKey) { this.lockKey = lockKey; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
}
