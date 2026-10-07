package com.evops.aqua.fulfill.external;

import java.util.Map;

/**
 * 海水养殖管理外部依赖网关：把订单拆单结果注册到外部系统。
 * 实现必须对同一 eventId 幂等（重复通知外部只生效一次）。
 */
public interface MarineManageGateway {

    /** 网关模式标识：sim / http */
    String mode();

    /**
     * 注册一次拆单事件；外部无响应（IO 异常/超时/非 2xx）必须抛 ExternalUnavailableException，
     * 绝不允许静默成功。同一 eventId 重复投递外部只计一次。
     */
    void registerSplit(String eventId, Map<String, Object> payload);
}
