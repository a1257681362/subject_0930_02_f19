package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.service.FishingEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 捕捞预约模块追溯入口：变更证据按对象或 requestId 查询。
 */
@RestController
@RequestMapping("/api/aqua/fishing")
public class FishingEvidenceController {

    private final FishingEvidenceService evidenceService;

    public FishingEvidenceController(FishingEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/evidence")
    public ApiResponse<List<FishingChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                             @RequestParam(required = false) Long entityId,
                                                             @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
