package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.DailyReportReviewRequest;
import com.evops.aqua.sensor.dto.ReviewResolveRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.entity.SensorReviewTask;
import com.evops.aqua.sensor.service.SensorDailyReportService;
import com.evops.aqua.sensor.service.SensorEvidenceService;
import com.evops.aqua.sensor.service.SensorReviewService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 传感器数据运营：异常读数待复核队列、日报审核、变更证据追溯。
 */
@RestController
@RequestMapping("/api/aqua/sensor")
public class SensorOpsController {

    private final SensorReviewService reviewService;
    private final SensorDailyReportService dailyReportService;
    private final SensorEvidenceService evidenceService;

    public SensorOpsController(SensorReviewService reviewService,
                               SensorDailyReportService dailyReportService,
                               SensorEvidenceService evidenceService) {
        this.reviewService = reviewService;
        this.dailyReportService = dailyReportService;
        this.evidenceService = evidenceService;
    }

    /** 异常读数待复核队列 */
    @GetMapping("/review-queue")
    public ApiResponse<List<SensorReviewTask>> reviewQueue(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Long pondId) {
        return ApiResponse.ok(reviewService.listQueue(status, pondId));
    }

    /** 复核：CONFIRMED 确认有效补入未审核日报；EXCLUDED 剔除永不计入 */
    @PostMapping("/review-queue/{id}/resolve")
    public ApiResponse<SensorReviewTask> resolve(@PathVariable Long id,
                                                 @Validated @RequestBody ReviewResolveRequest request) {
        return ApiResponse.ok(reviewService.resolve(id, request));
    }

    /** 日报查询：按池/日/指标过滤 */
    @GetMapping("/daily-reports")
    public ApiResponse<List<SensorDailyReport>> dailyReports(
            @RequestParam(required = false) Long pondId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String metricType) {
        return ApiResponse.ok(dailyReportService.list(pondId, reportDate, metricType));
    }

    /** 日报审核：审核后区间锁定，迟到数据一律拒绝补充；重复审核幂等 */
    @PostMapping("/daily-reports/{id}/review")
    public ApiResponse<SensorDailyReport> reviewReport(@PathVariable Long id,
                                                       @Validated @RequestBody DailyReportReviewRequest request) {
        return ApiResponse.ok(dailyReportService.review(id, request.getReviewedByName(), request.getRequestId()));
    }

    /** 变更证据：按对象或幂等请求号追溯 */
    @GetMapping("/evidence")
    public ApiResponse<List<SensorChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                            @RequestParam(required = false) Long entityId,
                                                            @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
