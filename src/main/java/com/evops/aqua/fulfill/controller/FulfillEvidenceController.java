package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.service.FulfillEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 履约模块变更证据查询：按对象或 requestId 追溯全部写操作（含外部同步成败）。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/evidence")
public class FulfillEvidenceController {

    private final FulfillEvidenceService evidenceService;

    public FulfillEvidenceController(FulfillEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<FulfillChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                         @RequestParam(required = false) Long entityId,
                                                         @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
