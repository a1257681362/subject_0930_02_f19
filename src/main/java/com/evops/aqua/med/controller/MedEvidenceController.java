package com.evops.aqua.med.controller;

import com.evops.aqua.med.entity.MedChangeEvidence;
import com.evops.aqua.med.entity.MedWithdrawalLock;
import com.evops.aqua.med.service.MedEvidenceService;
import com.evops.aqua.med.service.MedWithdrawalGuard;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用药模块追溯入口：变更证据查询 + 池塘休药锁定查询。
 */
@RestController
@RequestMapping("/api/aqua/med")
public class MedEvidenceController {

    private final MedEvidenceService evidenceService;
    private final MedWithdrawalGuard withdrawalGuard;

    public MedEvidenceController(MedEvidenceService evidenceService, MedWithdrawalGuard withdrawalGuard) {
        this.evidenceService = evidenceService;
        this.withdrawalGuard = withdrawalGuard;
    }

    @GetMapping("/evidence")
    public ApiResponse<List<MedChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                         @RequestParam(required = false) Long entityId,
                                                         @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }

    @GetMapping("/withdrawal-locks")
    public ApiResponse<MedWithdrawalLock> lockOfPond(@RequestParam Long pondId) {
        return ApiResponse.ok(withdrawalGuard.lockOfPond(pondId));
    }
}
