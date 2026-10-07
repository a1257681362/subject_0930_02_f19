package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.PointRegisterRequest;
import com.evops.aqua.fulfill.entity.DeliveryPoint;
import com.evops.aqua.fulfill.service.DeliveryPointService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货点入口：登记与查询。替换收货点走子单改点审批接口。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/points")
public class DeliveryPointController {

    private final DeliveryPointService pointService;

    public DeliveryPointController(DeliveryPointService pointService) {
        this.pointService = pointService;
    }

    @PostMapping
    public ApiResponse<DeliveryPoint> register(@Validated @RequestBody PointRegisterRequest request) {
        return ApiResponse.ok(pointService.register(request));
    }

    @GetMapping
    public ApiResponse<List<DeliveryPoint>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(pointService.list(status));
    }
}
