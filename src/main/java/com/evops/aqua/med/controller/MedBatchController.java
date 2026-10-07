package com.evops.aqua.med.controller;

import com.evops.aqua.med.dto.MedBatchInRequest;
import com.evops.aqua.med.entity.MedBatch;
import com.evops.aqua.med.service.MedBatchService;
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
 * 药品批号库存入口。
 */
@RestController
@RequestMapping("/api/aqua/med/batches")
public class MedBatchController {

    private final MedBatchService batchService;

    public MedBatchController(MedBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ApiResponse<MedBatch> stockIn(@Validated @RequestBody MedBatchInRequest request) {
        return ApiResponse.ok(batchService.stockIn(request));
    }

    @GetMapping
    public ApiResponse<List<MedBatch>> list(@RequestParam(required = false) Long drugId) {
        return ApiResponse.ok(batchService.list(drugId));
    }
}
