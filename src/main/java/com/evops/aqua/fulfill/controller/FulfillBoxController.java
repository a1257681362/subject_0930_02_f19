package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.BoxIntakeRequest;
import com.evops.aqua.fulfill.entity.FulfillBox;
import com.evops.aqua.fulfill.service.FulfillBoxService;
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
 * 箱入口：捕捞后入冷藏库（入库温度/箱号/称重结果）、按子单查箱。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/deliveries/{deliveryId}/boxes")
public class FulfillBoxController {

    private final FulfillBoxService boxService;

    public FulfillBoxController(FulfillBoxService boxService) {
        this.boxService = boxService;
    }

    @PostMapping
    public ApiResponse<FulfillBox> intake(@PathVariable Long deliveryId,
                                          @Validated @RequestBody BoxIntakeRequest request) {
        return ApiResponse.ok(boxService.intake(deliveryId, request));
    }

    @GetMapping
    public ApiResponse<List<FulfillBox>> list(@PathVariable Long deliveryId,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(boxService.boxesOf(deliveryId, status));
    }
}
