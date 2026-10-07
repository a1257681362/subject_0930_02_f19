package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.ColdStoreRegisterRequest;
import com.evops.aqua.fulfill.entity.ColdStore;
import com.evops.aqua.fulfill.service.ColdStoreService;
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
 * 冷藏库入口：登记与查询。入库重量账由箱入库/交接接口维护。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/cold-stores")
public class ColdStoreController {

    private final ColdStoreService storeService;

    public ColdStoreController(ColdStoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    public ApiResponse<ColdStore> register(@Validated @RequestBody ColdStoreRegisterRequest request) {
        return ApiResponse.ok(storeService.register(request));
    }

    @GetMapping
    public ApiResponse<List<ColdStore>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(storeService.list(status));
    }
}
