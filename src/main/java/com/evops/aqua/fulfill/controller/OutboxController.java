package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.entity.FulfillOutbox;
import com.evops.aqua.fulfill.service.OutboxService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 发件箱入口：手工触发补偿扫描（与 Quartz 定时同逻辑）、查询投递状态。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/outbox")
public class OutboxController {

    private final OutboxService outboxService;

    public OutboxController(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scan() {
        return ApiResponse.ok(outboxService.scan());
    }

    @GetMapping
    public ApiResponse<List<FulfillOutbox>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(outboxService.list(status));
    }
}
