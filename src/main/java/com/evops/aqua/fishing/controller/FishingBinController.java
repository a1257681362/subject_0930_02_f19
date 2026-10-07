package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.BinInspectRequest;
import com.evops.aqua.fishing.dto.BinRegisterRequest;
import com.evops.aqua.fishing.entity.FishingBin;
import com.evops.aqua.fishing.service.FishingBinService;
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
 * 库位入口：登记（按规格建仓）、查询、检验（状态与待检标识同步复位）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/bins")
public class FishingBinController {

    private final FishingBinService binService;

    public FishingBinController(FishingBinService binService) {
        this.binService = binService;
    }

    @PostMapping
    public ApiResponse<FishingBin> register(@Validated @RequestBody BinRegisterRequest request) {
        return ApiResponse.ok(binService.register(request));
    }

    @GetMapping
    public ApiResponse<List<FishingBin>> list(@RequestParam(required = false) String specCode,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(binService.list(specCode, status));
    }

    @PostMapping("/{id}/inspect")
    public ApiResponse<FishingBin> inspect(@PathVariable Long id,
                                           @Validated @RequestBody BinInspectRequest request) {
        return ApiResponse.ok(binService.inspect(id, request));
    }
}
