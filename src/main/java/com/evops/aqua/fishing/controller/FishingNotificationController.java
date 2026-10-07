package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.NotificationAckRequest;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.service.FishingNotificationService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户通知入口：按订单/状态查询；客户签收留回执（重复签收不覆盖首次回执）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/notifications")
public class FishingNotificationController {

    private final FishingNotificationService notificationService;

    public FishingNotificationController(FishingNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<FishingNotification>> list(@RequestParam(required = false) Long orderId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(notificationService.list(orderId, status));
    }

    @PostMapping("/{id}/ack")
    public ApiResponse<FishingNotification> ack(@PathVariable Long id,
                                                @Validated @RequestBody NotificationAckRequest request) {
        return ApiResponse.ok(notificationService.ack(id, request));
    }
}
