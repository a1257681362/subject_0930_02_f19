package com.evops.aqua.wq.controller;

import com.evops.aqua.wq.dto.PlanCreateRequest;
import com.evops.aqua.wq.dto.PlanUpdateRequest;
import com.evops.aqua.wq.dto.TaskGenerateRequest;
import com.evops.aqua.wq.entity.WqPlan;
import com.evops.aqua.wq.service.WqPlanService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水质采样计划：按池生成采样任务。
 */
@RestController
@RequestMapping("/api/aqua/wq/plans")
public class WqPlanController {

    private final WqPlanService planService;
    private final WqTaskService taskService;

    public WqPlanController(WqPlanService planService, WqTaskService taskService) {
        this.planService = planService;
        this.taskService = taskService;
    }

    @PostMapping
    public ApiResponse<WqPlan> create(@Validated @RequestBody PlanCreateRequest request) {
        return ApiResponse.ok(planService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WqPlan> update(@PathVariable Long id,
                                     @Validated @RequestBody PlanUpdateRequest request) {
        return ApiResponse.ok(planService.update(id, request));
    }

    @GetMapping
    public ApiResponse<List<WqPlan>> list(@RequestParam(required = false) Long pondId) {
        return ApiResponse.ok(planService.list(pondId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        WqPlan plan = planService.getOrThrow(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plan", plan);
        detail.put("pondIds", planService.listPondIds(id));
        return ApiResponse.ok(detail);
    }

    /**
     * 计划按池生成任务（幂等：重复请求不会产生第二条任务）。
     */
    @PostMapping("/{id}/tasks/generate")
    public ApiResponse<Map<String, Object>> generate(@PathVariable Long id,
                                                    @RequestBody(required = false) TaskGenerateRequest request) {
        TaskGenerateRequest body = request == null ? new TaskGenerateRequest() : request;
        body.setPlanId(id);
        return ApiResponse.ok(taskService.generate(body));
    }
}
