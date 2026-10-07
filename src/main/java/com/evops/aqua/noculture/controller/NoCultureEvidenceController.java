package com.evops.aqua.noculture.controller;

import com.evops.aqua.noculture.entity.NoCultureChangeEvidence;
import com.evops.aqua.noculture.service.NoCultureEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 禁养模块变更证据查询：按对象或 requestId 追溯。
 * 规则登记/撤销、每次冻结与解冻（含仍被他规冻结的未解冻）均可查，每条均带操作者。
 */
@RestController
@RequestMapping("/api/aqua/noculture/evidence")
public class NoCultureEvidenceController {

    private final NoCultureEvidenceService evidenceService;

    public NoCultureEvidenceController(NoCultureEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<NoCultureChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                           @RequestParam(required = false) Long entityId,
                                                           @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
