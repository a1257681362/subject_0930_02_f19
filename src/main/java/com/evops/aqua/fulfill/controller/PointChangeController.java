package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.PointChangeReviewRequest;
import com.evops.aqua.fulfill.entity.DeliveryPointChange;
import com.evops.aqua.fulfill.service.FulfillDeliveryService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收货点替换审批入口：通过（只改点不碰金额数量）/ 驳回。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/point-changes")
public class PointChangeController {

    private final FulfillDeliveryService deliveryService;

    public PointChangeController(FulfillDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<DeliveryPointChange> approve(@PathVariable Long id,
                                                     @Validated @RequestBody PointChangeReviewRequest request) {
        return ApiResponse.ok(deliveryService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<DeliveryPointChange> reject(@PathVariable Long id,
                                                    @Validated @RequestBody PointChangeReviewRequest request) {
        return ApiResponse.ok(deliveryService.reject(id, request));
    }
}
