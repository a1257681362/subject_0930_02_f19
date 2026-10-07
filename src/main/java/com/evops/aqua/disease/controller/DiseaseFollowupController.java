package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.entity.DiseaseFollowupTask;
import com.evops.aqua.disease.service.DiseaseFollowupService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 疫病复查任务：定时任务按日自动生成（幂等，重启不多写）；
 * 本接口提供人工触发扫描（与定时任务同一逻辑）与查询。
 */
@RestController
@RequestMapping("/api/aqua/disease/followup-tasks")
public class DiseaseFollowupController {

    private final DiseaseFollowupService followupService;

    public DiseaseFollowupController(DiseaseFollowupService followupService) {
        this.followupService = followupService;
    }

    /** 人工触发一次扫描（与定时任务同逻辑、同幂等键），用于运维核查。 */
    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scan(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taskDate) {
        LocalDate date = taskDate == null ? LocalDate.now() : taskDate;
        return ApiResponse.ok(followupService.generateDailyTasks(date));
    }

    @GetMapping
    public ApiResponse<List<DiseaseFollowupTask>> list(
            @RequestParam(required = false) Long observationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taskDate) {
        return ApiResponse.ok(followupService.list(observationId, taskDate));
    }
}
