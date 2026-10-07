package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.entity.DiseaseChangeEvidence;
import com.evops.aqua.disease.service.DiseaseEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 疫病模块变更证据查询：按对象或 requestId 追溯，每条证据均带操作者。
 */
@RestController
@RequestMapping("/api/aqua/disease/evidence")
public class DiseaseEvidenceController {

    private final DiseaseEvidenceService evidenceService;

    public DiseaseEvidenceController(DiseaseEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<DiseaseChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                         @RequestParam(required = false) Long entityId,
                                                         @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
