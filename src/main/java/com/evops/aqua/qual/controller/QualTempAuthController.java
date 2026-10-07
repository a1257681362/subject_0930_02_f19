package com.evops.aqua.qual.controller;

import com.evops.aqua.qual.dto.TempAuthGrantRequest;
import com.evops.aqua.qual.entity.TempAuth;
import com.evops.aqua.qual.service.TempAuthService;
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
 * 临时授权：必须带结束时间与审批单号；到点自动失效。
 */
@RestController
@RequestMapping("/api/aqua/qual/temp-auths")
public class QualTempAuthController {

    private final TempAuthService tempAuthService;

    public QualTempAuthController(TempAuthService tempAuthService) {
        this.tempAuthService = tempAuthService;
    }

    @PostMapping
    public ApiResponse<TempAuth> grant(@Validated @RequestBody TempAuthGrantRequest request) {
        return ApiResponse.ok(tempAuthService.grant(request));
    }

    @GetMapping
    public ApiResponse<List<TempAuth>> list(@RequestParam Long personId) {
        return ApiResponse.ok(tempAuthService.listByPerson(personId));
    }
}
