package com.evops.aqua.qual.controller;

import com.evops.aqua.qual.dto.PermissionCheckRequest;
import com.evops.aqua.qual.entity.QualChangeEvidence;
import com.evops.aqua.qual.entity.QualReminder;
import com.evops.aqua.qual.service.QualAuthService;
import com.evops.aqua.qual.service.QualEvidenceService;
import com.evops.aqua.qual.service.QualScanService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限核验 / 资质扫描 / 到期提醒 / 变更证据。
 */
@RestController
@RequestMapping("/api/aqua/qual")
public class QualAccessController {

    private final QualAuthService qualAuthService;
    private final QualScanService scanService;
    private final QualEvidenceService evidenceService;

    public QualAccessController(QualAuthService qualAuthService, QualScanService scanService,
                                QualEvidenceService evidenceService) {
        this.qualAuthService = qualAuthService;
        this.scanService = scanService;
        this.evidenceService = evidenceService;
    }

    /** 权限核验：menu: 精确匹配，api: 精确或前缀匹配；返回允许与否与收回原因。 */
    @PostMapping("/access/check")
    public ApiResponse<Map<String, Object>> check(
            @Validated @RequestBody PermissionCheckRequest request) {
        return ApiResponse.ok(qualAuthService.check(request.getPersonCode(), request.getPermission()));
    }

    /** 手工触发资质扫描（与 Quartz 定时同一逻辑）：到期提醒 + 过期证书/临时授权置位留证。 */
    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scan() {
        return ApiResponse.ok(scanService.scanNow());
    }

    @GetMapping("/reminders")
    public ApiResponse<List<QualReminder>> reminders(@RequestParam(required = false) Long personId) {
        return ApiResponse.ok(scanService.listReminders(personId));
    }

    @GetMapping("/evidence")
    public ApiResponse<List<QualChangeEvidence>> evidence(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
