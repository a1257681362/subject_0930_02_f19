package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.DeliverySplitRequest;
import com.evops.aqua.fulfill.dto.PointChangeSubmitRequest;
import com.evops.aqua.fulfill.entity.DeliveryPointChange;
import com.evops.aqua.fulfill.entity.FulfillDelivery;
import com.evops.aqua.fulfill.service.FulfillDeliveryService;
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
 * 履约子单入口：再拆（只切数量不改金额口径）、提交替换收货点申请。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/deliveries")
public class FulfillDeliveryController {

    private final FulfillDeliveryService deliveryService;

    public FulfillDeliveryController(FulfillDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{id}/split")
    public ApiResponse<FulfillDelivery> split(@PathVariable Long id,
                                              @Validated @RequestBody DeliverySplitRequest request) {
        return ApiResponse.ok(deliveryService.split(id, request));
    }

    @PostMapping("/{id}/point-changes")
    public ApiResponse<DeliveryPointChange> submitPointChange(
            @PathVariable Long id,
            @Validated @RequestBody PointChangeSubmitRequest request) {
        return ApiResponse.ok(deliveryService.submitPointChange(id, request));
    }

    @GetMapping("/{id}/point-changes")
    public ApiResponse<List<DeliveryPointChange>> changes(@PathVariable Long id,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(deliveryService.changesOf(id, status));
    }
}
