package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.dto.ReleaseRequest;
import com.evops.aqua.disease.entity.DiseaseIsolation;
import com.evops.aqua.disease.entity.DiseaseStockMovement;
import com.evops.aqua.disease.service.DiseaseIsolationService;
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
 * 隔离单：解除隔离必须兽医复核；原池与隔离池之间的数量变化逐笔可查。
 */
@RestController
@RequestMapping("/api/aqua/disease/isolations")
public class DiseaseIsolationController {

    private final DiseaseIsolationService isolationService;

    public DiseaseIsolationController(DiseaseIsolationService isolationService) {
        this.isolationService = isolationService;
    }

    @PostMapping("/{id}/release")
    public ApiResponse<DiseaseIsolation> release(@PathVariable Long id,
                                                 @Validated @RequestBody ReleaseRequest request) {
        return ApiResponse.ok(isolationService.release(id, request));
    }

    @GetMapping
    public ApiResponse<List<DiseaseIsolation>> list(@RequestParam(required = false) Long batchId,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(isolationService.list(batchId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiseaseIsolation> detail(@PathVariable Long id) {
        return ApiResponse.ok(isolationService.getOrThrow(id));
    }

    /** 数量变化逐笔账：原池与隔离池之间每笔调入调出（含操作者）。 */
    @GetMapping("/movements")
    public ApiResponse<List<DiseaseStockMovement>> movements(@RequestParam(required = false) Long batchId,
                                                             @RequestParam(required = false) Long isolationId) {
        return ApiResponse.ok(isolationService.listMovements(batchId, isolationId));
    }
}
