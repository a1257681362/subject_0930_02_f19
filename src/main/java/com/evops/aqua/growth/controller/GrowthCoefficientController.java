package com.evops.aqua.growth.controller;

import com.evops.aqua.growth.dto.CoefficientSaveRequest;
import com.evops.aqua.growth.entity.GrowthDeviceCoefficient;
import com.evops.aqua.growth.service.GrowthCoefficientService;
import com.evops.common.ApiResponse;
import org.apache.shiro.SecurityUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备校正系数：按设备 + 版本保存，旧生效版本自动停用；历史测量冻结版本不重算。
 */
@RestController
@RequestMapping("/api/aqua/growth/coefficients")
public class GrowthCoefficientController {

    private final GrowthCoefficientService coefficientService;

    public GrowthCoefficientController(GrowthCoefficientService coefficientService) {
        this.coefficientService = coefficientService;
    }

    @PostMapping
    public ApiResponse<GrowthDeviceCoefficient> save(@Validated @RequestBody CoefficientSaveRequest request) {
        if (request.getOperator() == null || request.getOperator().trim().isEmpty()) {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            request.setOperator(principal == null ? "system" : String.valueOf(principal));
        }
        return ApiResponse.ok(coefficientService.save(request));
    }

    @GetMapping
    public ApiResponse<List<GrowthDeviceCoefficient>> list(@RequestParam(required = false) String deviceCode) {
        return ApiResponse.ok(coefficientService.list(deviceCode));
    }
}
