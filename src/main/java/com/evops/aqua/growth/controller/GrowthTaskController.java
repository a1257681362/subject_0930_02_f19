package com.evops.aqua.growth.controller;

import com.evops.aqua.growth.dto.SampleRecordRequest;
import com.evops.aqua.growth.dto.SampleReviewRequest;
import com.evops.aqua.growth.dto.TaskConfirmRequest;
import com.evops.aqua.growth.dto.GrowthTaskSubmitRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthHarvestForecast;
import com.evops.aqua.growth.entity.GrowthMeasurementSample;
import com.evops.aqua.growth.entity.GrowthMeasurementTask;
import com.evops.aqua.growth.service.GrowthEvidenceService;
import com.evops.aqua.growth.service.GrowthTaskService;
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
import java.util.Map;

/**
 * 生长测量任务：现场录入体重长度（校正求均值）、异常只标记不删除并填复核理由、
 * 测量员提交、主管确认后更新预测出池量；支持从测量记录反查抽样方案/处理人/异常原因。
 */
@RestController
@RequestMapping("/api/aqua/growth")
public class GrowthTaskController {

    private final GrowthTaskService taskService;
    private final GrowthEvidenceService evidenceService;

    public GrowthTaskController(GrowthTaskService taskService, GrowthEvidenceService evidenceService) {
        this.taskService = taskService;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<GrowthMeasurementTask>> listTasks(@RequestParam(required = false) Long pondId,
                                                              @RequestParam(required = false) Long batchId,
                                                              @RequestParam(required = false) Long planId,
                                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(taskService.listTasks(pondId, batchId, planId, status));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.taskDetail(id));
    }

    /** 现场录入样本体重长度：按冻结系数校正并按未标记异常样本求均值（异常样本只标记不删除）。 */
    @PostMapping("/tasks/{id}/samples")
    public ApiResponse<GrowthMeasurementTask> recordSamples(@PathVariable Long id,
                                                            @Validated @RequestBody SampleRecordRequest request) {
        return ApiResponse.ok(taskService.recordSamples(id, request));
    }

    /** 异常复核：标记/改判异常必须填复核理由与复核人，绝不删除样本。 */
    @PostMapping("/tasks/{id}/samples/{seqNo}/review")
    public ApiResponse<GrowthMeasurementSample> reviewSample(@PathVariable Long id,
                                                             @PathVariable Integer seqNo,
                                                             @Validated @RequestBody SampleReviewRequest request) {
        return ApiResponse.ok(taskService.reviewSample(id, seqNo, request));
    }

    /** 测量员提交：校验抽样数量与允许偏差，异常样本须有复核理由；重复提交不产生第二条。 */
    @PostMapping("/tasks/{id}/submit")
    public ApiResponse<GrowthMeasurementTask> submit(@PathVariable Long id,
                                                     @RequestBody(required = false) GrowthTaskSubmitRequest request) {
        GrowthTaskSubmitRequest body = request == null ? new GrowthTaskSubmitRequest() : request;
        return ApiResponse.ok(taskService.submit(id, body));
    }

    /** 主管确认/驳回：通过后才写预测出池量，历史测量不随新系数重算。 */
    @PostMapping("/tasks/{id}/confirm")
    public ApiResponse<GrowthMeasurementTask> confirm(@PathVariable Long id,
                                                      @Validated @RequestBody TaskConfirmRequest request) {
        return ApiResponse.ok(taskService.confirm(id, request));
    }

    /**
     * 从海水养殖管理的测量记录反查抽样方案：按池塘 + 批次（可加测量日/任务号）
     * 定位原单（planNo/taskNo）、处理人及异常原因。
     */
    @GetMapping("/reverse-trace")
    public ApiResponse<Map<String, Object>> reverseTrace(
            @RequestParam(required = false) Long pondId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate measureDate,
            @RequestParam(required = false) String taskNo) {
        return ApiResponse.ok(taskService.reverseTrace(pondId, batchId, measureDate, taskNo));
    }

    /** 预测出池量列表：仅含主管确认后写入的结果。 */
    @GetMapping("/forecasts")
    public ApiResponse<List<GrowthHarvestForecast>> forecasts(@RequestParam(required = false) Long pondId,
                                                              @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(taskService.listForecasts(pondId, batchId));
    }

    /** 变更证据查询：可按对象、动作或幂等请求号追溯处理人。 */
    @GetMapping("/evidence")
    public ApiResponse<List<GrowthChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                            @RequestParam(required = false) Long entityId,
                                                            @RequestParam(required = false) String requestId,
                                                            @RequestParam(required = false) String action) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId, action));
    }
}
