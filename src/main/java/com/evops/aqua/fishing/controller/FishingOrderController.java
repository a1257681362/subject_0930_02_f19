package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.OrderCreateRequest;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.service.FishingOrderService;
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
import java.util.Map;

/**
 * 客户订单入口：创建（按规格/重量区间/交付时间拆分预约）、列表、
 * 订单视图（已完成订单附当时冻结的分级规则快照）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/orders")
public class FishingOrderController {

    private final FishingOrderService orderService;

    public FishingOrderController(FishingOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<FishingOrder> create(@Validated @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<FishingOrder>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long id) {
        return ApiResponse.ok(orderService.view(id));
    }
}
