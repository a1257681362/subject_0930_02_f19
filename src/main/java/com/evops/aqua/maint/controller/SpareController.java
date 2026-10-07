package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.SpareCatalogRequest;
import com.evops.aqua.maint.dto.SparePartStockRequest;
import com.evops.aqua.maint.entity.SpareCatalog;
import com.evops.aqua.maint.entity.SparePart;
import com.evops.aqua.maint.service.SpareService;
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
 * 备件：品类目录 + 一物一序列号实例（在库/占用/装机/报废留存）。
 */
@RestController
@RequestMapping("/api/aqua/maint")
public class SpareController {

    private final SpareService spareService;

    public SpareController(SpareService spareService) {
        this.spareService = spareService;
    }

    @PostMapping("/spare-catalogs")
    public ApiResponse<SpareCatalog> registerCatalog(@Validated @RequestBody SpareCatalogRequest request) {
        return ApiResponse.ok(spareService.registerCatalog(request));
    }

    /** 实物入库：一物一序列号，重复序列号返回首次实例。 */
    @PostMapping("/spare-parts")
    public ApiResponse<SparePart> stockIn(@Validated @RequestBody SparePartStockRequest request) {
        return ApiResponse.ok(spareService.stockIn(request));
    }

    @GetMapping("/spare-parts")
    public ApiResponse<List<SparePart>> listParts(@RequestParam(required = false) Long catalogId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(spareService.listParts(catalogId, status));
    }
}
