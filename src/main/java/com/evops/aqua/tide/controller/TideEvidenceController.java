package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.service.TideEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 潮汐模块变更证据：按对象或 requestId 追溯，全程留证。
 */
@RestController
@RequestMapping("/api/aqua/tide/evidence")
public class TideEvidenceController {

    private final TideEvidenceService evidenceService;

    public TideEvidenceController(TideEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<TideChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                      @RequestParam(required = false) Long entityId,
                                                      @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
