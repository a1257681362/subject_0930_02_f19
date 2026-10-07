package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.external.SimMarineManageGateway;
import com.evops.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟海水养殖管理控制入口（仅 aqua.fulfill.gateway.mode=sim 时存在）：
 * 切换 UP/DOWN、重置已收事件、查询对端实际收到的事件数——用于验收「无响应不假装成功、恢复不重复通知」。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/gateway/sim")
@ConditionalOnProperty(name = "aqua.fulfill.gateway.mode", havingValue = "sim", matchIfMissing = true)
public class SimGatewayController {

    private final SimMarineManageGateway gateway;

    public SimGatewayController(SimMarineManageGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/up")
    public ApiResponse<Map<String, Object>> up() {
        gateway.up();
        return ApiResponse.ok(stateMap());
    }

    @PostMapping("/down")
    public ApiResponse<Map<String, Object>> down() {
        gateway.down();
        return ApiResponse.ok(stateMap());
    }

    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        gateway.reset();
        return ApiResponse.ok(stateMap());
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> state() {
        return ApiResponse.ok(stateMap());
    }

    private Map<String, Object> stateMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("up", gateway.isUp());
        map.put("receivedCount", gateway.receivedCount());
        map.put("lastEventId", gateway.getLastEventId());
        return map;
    }
}
