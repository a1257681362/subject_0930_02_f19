package com.evops.aqua.wq.controller;

import com.evops.aqua.wq.dto.BackfillRequest;
import com.evops.aqua.wq.dto.TaskDraftRequest;
import com.evops.aqua.wq.dto.TaskReviewRequest;
import com.evops.aqua.wq.dto.TaskSubmitRequest;
import com.evops.aqua.wq.entity.WqBackfill;
import com.evops.aqua.wq.entity.WqChangeEvidence;
import com.evops.aqua.wq.entity.WqHealthPoint;
import com.evops.aqua.wq.entity.WqTask;
import com.evops.aqua.wq.service.WqEvidenceService;
import com.evops.aqua.wq.service.WqTaskService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 水质采样任务：草稿暂存、提交（缺测须说明原因）、补录（只追加）、主管审核、健康曲线、证据。
 */
@RestController
@RequestMapping("/api/aqua/wq")
public class WqTaskController {

    private final WqTaskService taskService;
    private final WqEvidenceService evidenceService;

    public WqTaskController(WqTaskService taskService, WqEvidenceService evidenceService) {
        this.taskService = taskService;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<WqTask>> listTasks(@RequestParam(required = false) Long pondId,
                                               @RequestParam(required = false) Long planId,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(taskService.listTasks(pondId, planId, status));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.taskDetail(id));
    }

    /** 采样员暂存草稿：可只录部分指标，缺测项此时不强制原因 */
    @PutMapping("/tasks/{id}/draft")
    public ApiResponse<WqTask> saveDraft(@PathVariable Long id,
                                        @Validated @RequestBody TaskDraftRequest request) {
        return ApiResponse.ok(taskService.saveDraft(id, request.getItems()));
    }

    /** 采样员提交：缺测项必须说明原因；重复提交不产生第二条结果 */
    @PostMapping("/tasks/{id}/submit")
    public ApiResponse<WqTask> submit(@PathVariable Long id,
                                     @RequestBody(required = false) TaskSubmitRequest request) {
        TaskSubmitRequest body = request == null ? new TaskSubmitRequest() : request;
        return ApiResponse.ok(taskService.submit(id, body.getItems(), body.getRequestId()));
    }

    /** 主管审核：通过后才进入池塘健康曲线；重复审核不重复写曲线 */
    @PostMapping("/tasks/{id}/review")
    public ApiResponse<WqTask> review(@PathVariable Long id,
                                     @Validated @RequestBody TaskReviewRequest request) {
        return ApiResponse.ok(taskService.review(id, request));
    }

    /** 缺测/历史补录：只能追加，不能覆盖原值；requestId 幂等 */
    @PostMapping("/backfills")
    public ApiResponse<WqBackfill> backfill(@Validated @RequestBody BackfillRequest request) {
        return ApiResponse.ok(taskService.backfill(request));
    }

    /** 池塘健康曲线：仅含主管审核通过的采样值 */
    @GetMapping("/ponds/{pondId}/health-curve")
    public ApiResponse<List<WqHealthPoint>> healthCurve(@PathVariable Long pondId,
                                                       @RequestParam(required = false) String indicatorCode) {
        return ApiResponse.ok(taskService.healthCurve(pondId, indicatorCode));
    }

    /** 修改证据查询：可按对象或幂等请求号追溯 */
    @GetMapping("/evidence")
    public ApiResponse<List<WqChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                        @RequestParam(required = false) Long entityId,
                                                        @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
