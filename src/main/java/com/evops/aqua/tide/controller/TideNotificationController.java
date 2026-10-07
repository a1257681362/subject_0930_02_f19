package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.AckRequest;
import com.evops.aqua.tide.entity.TideNotification;
import com.evops.aqua.tide.service.TideNotificationService;
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
 * 窗口关闭通知：发给受影响班组，班组签收留回执。
 */
@RestController
@RequestMapping("/api/aqua/tide/notifications")
public class TideNotificationController {

    private final TideNotificationService notificationService;

    public TideNotificationController(TideNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<TideNotification>> list(@RequestParam(required = false) String crewName,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long windowId) {
        return ApiResponse.ok(notificationService.list(crewName, status, windowId));
    }

    @GetMapping("/{id}")
    public ApiResponse<TideNotification> detail(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.getOrThrow(id));
    }

    /** 班组签收：留回执（签收人+时间+回执备注）；重复签收幂等返回同一回执。 */
    @PostMapping("/{id}/ack")
    public ApiResponse<TideNotification> ack(@PathVariable Long id,
                                             @Validated @RequestBody AckRequest request) {
        return ApiResponse.ok(notificationService.ack(id, request));
    }
}
