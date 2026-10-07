package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.PlanAdjustRequest;
import com.evops.aqua.maint.dto.MaintPlanCreateRequest;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.PlanPart;
import com.evops.aqua.maint.service.PlanService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 保养计划：运行小时与日历双轨，备件模板随计划挂接；调整只影响未生成工单。
 */
@RestController
@RequestMapping("/api/aqua/maint/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ApiResponse<MaintPlan> create(@Validated @RequestBody MaintPlanCreateRequest request) {
        return ApiResponse.ok(planService.create(request));
    }

    /** 调整计划（间隔/开关/起算日）：version_no 递增，只影响此后生成的工单。 */
    @PostMapping("/{id}/adjust")
    public ApiResponse<MaintPlan> adjust(@PathVariable Long id,
                                         @Validated @RequestBody PlanAdjustRequest request) {
        return ApiResponse.ok(planService.adjust(id, request));
    }

    @GetMapping
    public ApiResponse<List<MaintPlan>> list(@RequestParam(required = false) Long deviceId,
                                             @RequestParam(required = false) String status) {
        return ApiResponse.ok(planService.list(deviceId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaintPlan> detail(@PathVariable Long id) {
        return ApiResponse.ok(planService.getOrThrow(id));
    }

    @GetMapping("/{id}/parts")
    public ApiResponse<List<PlanPart>> parts(@PathVariable Long id) {
        return ApiResponse.ok(planService.listParts(id));
    }
}
