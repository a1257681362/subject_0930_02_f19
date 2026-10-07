package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.entity.ConcurrentAudit;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.service.ConcurrencyGuard;
import com.evops.aqua.maint.service.MaintEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 保养模块审计出口：
 * - 变更证据：登记/调整/生成/派工/开工/换件/完工/验收/安全确认/关闭/升级全程留证；
 * - 并发裁决审计：同一操作并发时谁生效、谁被拒绝、双方操作者与拒绝原因。
 */
@RestController
@RequestMapping("/api/aqua/maint")
public class MaintAuditController {

    private final MaintEvidenceService evidenceService;
    private final ConcurrencyGuard concurrencyGuard;

    public MaintAuditController(MaintEvidenceService evidenceService,
                                ConcurrencyGuard concurrencyGuard) {
        this.evidenceService = evidenceService;
        this.concurrencyGuard = concurrencyGuard;
    }

    @GetMapping("/evidence")
    public ApiResponse<List<MaintChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                           @RequestParam(required = false) Long entityId,
                                                           @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }

    @GetMapping("/concurrent-audits")
    public ApiResponse<List<ConcurrentAudit>> concurrentAudits(@RequestParam(required = false) String bizKey,
                                                               @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(concurrencyGuard.listAudit(bizKey, requestId));
    }
}
