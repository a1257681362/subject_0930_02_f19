package com.evops.aqua.maint.service;

/**
 * 同一操作并发被拒绝：消息中必须说明谁的请求生效、谁被拒绝。
 */
public class ConcurrentRejectedException extends IllegalArgumentException {

    public ConcurrentRejectedException(String message) {
        super(message);
    }
}
