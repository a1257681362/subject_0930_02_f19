package com.evops.aqua.report.controller;

import com.evops.aqua.report.dto.ReportGenerateRequest;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.entity.ReportJob;
import com.evops.aqua.report.service.ReportEvidenceService;
import com.evops.aqua.report.service.ReportJobService;
import com.evops.aqua.report.service.ReportQueryService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.Map;

/**
 * 水产监管月报生成/查询/导出。
 * 查询与导出按登录用户海区数据权限过滤（与海区授权管理共用同一口径）；
 * 手工生成同样产生任务行（失败保留异常链路与重试次数），每次成功生成只产生新版本快照。
 */
@RestController
@RequestMapping("/api/aqua/report/monthly")
public class ReportMonthlyController {

    private final ReportJobService jobService;
    private final ReportQueryService queryService;
    private final ReportEvidenceService evidenceService;

    public ReportMonthlyController(ReportJobService jobService,
                                   ReportQueryService queryService,
                                   ReportEvidenceService evidenceService) {
        this.jobService = jobService;
        this.queryService = queryService;
        this.evidenceService = evidenceService;
    }

    /** 手工生成：seaArea 必填单海区（导出/生成按海区隔离）；创建任务并执行一次。 */
    @PostMapping("/generate")
    public ApiResponse<ReportJob> generate(@RequestBody ReportGenerateRequest request) {
        String month = request.getReportMonth() == null || request.getReportMonth().trim().isEmpty()
                ? YearMonth.now().toString() : request.getReportMonth();
        if (request.getSeaArea() == null || request.getSeaArea().trim().isEmpty()) {
            throw new IllegalArgumentException("手工生成月报必须指定海区 seaArea");
        }
        ReportJob job = jobService.submitManual(month, request.getSeaArea(),
                request.getRequestId(), ReportEvidenceService.currentOperator());
        return ApiResponse.ok(job);
    }

    /** 月报列表：仅返回当前登录用户授权海区的固化快照。 */
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) String reportMonth,
                                    @RequestParam(required = false) String seaArea) {
        String username = ReportEvidenceService.currentOperator();
        return ApiResponse.ok(queryService.loadRows(username, reportMonth, seaArea));
    }

    /** 月报详情：逐指标口径版本与值（待补原样）、检验结论、同月份海区历史版本。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.detail(ReportEvidenceService.currentOperator(), id));
    }

    /**
     * CSV 导出：与页面同一 loadRows、同一海区权限过滤；
     * 每指标列带口径版本号，待补指标导出"待补"不写 0。
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String reportMonth,
            @RequestParam(required = false) String seaArea) {
        String username = ReportEvidenceService.currentOperator();
        String csv = queryService.exportCsv(username, reportMonth, seaArea);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"));
        String filename = "aqua-monthly-report"
                + (reportMonth == null ? "" : "-" + reportMonth)
                + (seaArea == null ? "" : "-" + seaArea) + ".csv";
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        evidenceService.record(ReportChangeEvidence.ENTITY_EXPORT, 0L,
                ReportChangeEvidence.ACTION_EXPORT, null, username, null, null,
                "按海区权限导出监管月报：月份=" + reportMonth + "，海区=" + seaArea);
        return org.springframework.http.ResponseEntity.ok().headers(headers)
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
