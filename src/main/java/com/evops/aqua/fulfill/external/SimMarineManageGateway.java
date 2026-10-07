package com.evops.aqua.fulfill.external;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟海水养殖管理：内存态，仅用于本地/验收环境。
 * - down 状态下 registerSplit 直接抛 ExternalUnavailableException（外部无响应不假装成功）；
 * - 按 eventId 去重：重复投递 receivedCount 不增（恢复后重发不重复通知）；
 * - reset 清空已收事件并恢复 UP（内存状态重启即失，发件箱在数据库中，扫描可恢复）。
 */
@Component
@ConditionalOnProperty(name = "aqua.fulfill.gateway.mode", havingValue = "sim", matchIfMissing = true)
public class SimMarineManageGateway implements MarineManageGateway {

    private volatile boolean up = true;
    private final Set<String> receivedEventIds = ConcurrentHashMap.newKeySet();
    private volatile String lastEventId;
    private volatile Map<String, Object> lastPayload;

    @Override
    public String mode() {
        return "sim";
    }

    @Override
    public void registerSplit(String eventId, Map<String, Object> payload) {
        if (!up) {
            throw new ExternalUnavailableException("海水养殖管理无响应（模拟网关 DOWN），eventId=" + eventId);
        }
        if (receivedEventIds.add(eventId)) {
            lastEventId = eventId;
            lastPayload = payload == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    public void up() {
        this.up = true;
    }

    public void down() {
        this.up = false;
    }

    public void reset() {
        this.up = true;
        this.receivedEventIds.clear();
        this.lastEventId = null;
        this.lastPayload = null;
    }

    public boolean isUp() {
        return up;
    }

    public int receivedCount() {
        return receivedEventIds.size();
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public Map<String, Object> getLastPayload() {
        return lastPayload;
    }
}
