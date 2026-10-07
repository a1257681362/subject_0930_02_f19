package com.evops.aqua.growth.controller;

import com.evops.aqua.growth.dto.GrowthPlanCreateRequest;
import com.evops.aqua.growth.dto.GrowthPlanUpdateRequest;
import com.evops.aqua.growth.dto.GrowthTaskGenerateRequest;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.service.GrowthPlanService;
import com.evops.aqua.growth.service.GrowthTaskService;
import com.evops.common.ApiResponse;
import org.apache.shiro.SecurityUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生长抽样方案：按池塘 + 批次配置抽样数量、测量频次、允许偏差，按方案生成测量任务。
 */
@RestController
@RequestMapping("/api/aqua/growth/plans")
public class GrowthPlanController {

    private final GrowthPlanService planService;
    private final GrowthTaskService taskService;

    public GrowthPlanController(GrowthPlanService planService, GrowthTaskService taskService) {
        this.planService = planService;
        this.taskService = taskService;
    }

    @PostMapping
    public ApiResponse<GrowthSamplingPlan> create(@Validated @RequestBody GrowthPlanCreateRequest request) {
        return ApiResponse.ok(planService.create(request, currentOperator()));
    }

    @PutMapping("/{id}")
    public ApiResponse<GrowthSamplingPlan> update(@PathVariable Long id,
                                                  @Validated @RequestBody GrowthPlanUpdateRequest request) {
        return ApiResponse.ok(planService.update(id, request, currentOperator()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<GrowthSamplingPlan> cancel(@PathVariable Long id) {
        return ApiResponse.ok(planService.cancel(id, currentOperator()));
    }

    @GetMapping
    public ApiResponse<List<GrowthSamplingPlan>> list(@RequestParam(required = false) Long pondId,
                                                      @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(planService.list(pondId, batchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        GrowthSamplingPlan plan = planService.getOrThrow(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plan", plan);
        return ApiResponse.ok(detail);
    }

    /** 按方案生成测量任务（幂等：同方案同池批同日不产生第二条）。 */
    @PostMapping("/{id}/tasks/generate")
    public ApiResponse<Map<String, Object>> generate(@PathVariable Long id,
                                                     @RequestBody(required = false) GrowthTaskGenerateRequest request) {
        GrowthTaskGenerateRequest body = request == null ? new GrowthTaskGenerateRequest() : request;
        return ApiResponse.ok(taskService.generate(id, body, currentOperator()));
    }

    private String currentOperator() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        return principal == null ? "system" : String.valueOf(principal);
    }
}
