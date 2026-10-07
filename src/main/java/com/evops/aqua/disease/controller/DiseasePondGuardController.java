package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.dto.PondGuardUpsertRequest;
import com.evops.aqua.disease.entity.PondGuard;
import com.evops.aqua.disease.service.PondGuardService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 池塘隔离调度档案：登记隔离池角色、容量、消毒有效期、调入权限。
 */
@RestController
@RequestMapping("/api/aqua/disease/pond-guards")
public class DiseasePondGuardController {

    private final PondGuardService guardService;

    public DiseasePondGuardController(PondGuardService guardService) {
        this.guardService = guardService;
    }

    @PostMapping
    public ApiResponse<PondGuard> upsert(@Validated @RequestBody PondGuardUpsertRequest request) {
        return ApiResponse.ok(guardService.upsert(request));
    }

    @GetMapping
    public ApiResponse<List<PondGuard>> list() {
        return ApiResponse.ok(guardService.list());
    }
}
