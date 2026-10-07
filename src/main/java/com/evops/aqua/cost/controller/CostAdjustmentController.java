package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.AdjustmentCreateRequest;
import com.evops.aqua.cost.dto.AdjustmentReviewRequest;
import com.evops.aqua.cost.entity.CostAdjustment;
import com.evops.aqua.cost.service.CostLedgerService;
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
 * 成本差异调整入口：建单待审核，通过才入账，驳回不动账。
 */
@RestController
@RequestMapping("/api/aqua/cost/adjustments")
public class CostAdjustmentController {

    private final CostLedgerService ledgerService;

    public CostAdjustmentController(CostLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ApiResponse<CostAdjustment> create(@Validated @RequestBody AdjustmentCreateRequest request) {
        return ApiResponse.ok(ledgerService.createAdjustment(request));
    }

    /** 列表按角色脱敏：生产角色看不到差异金额与凭证号。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long batchId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "PRODUCTION") String role) {
        return ApiResponse.ok(ledgerService.listAdjustmentsView(batchId, status, role));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<CostAdjustment> approve(@PathVariable Long id,
                                               @Validated @RequestBody AdjustmentReviewRequest request) {
        return ApiResponse.ok(ledgerService.reviewAdjustment(id, true, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<CostAdjustment> reject(@PathVariable Long id,
                                              @Validated @RequestBody AdjustmentReviewRequest request) {
        return ApiResponse.ok(ledgerService.reviewAdjustment(id, false, request));
    }
}
