package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.SensorRiskWatchToggleRequest;
import com.evops.aqua.sensor.entity.SensorRiskWatch;
import com.evops.aqua.sensor.service.SensorDailyReportService;
import com.evops.aqua.sensor.service.SensorRiskWatchService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 离线归并高风险观察开关：观察项「传感器补传造成日报重复」。
 * 置为观察后离线补传归并与日报汇总重算同步限制；解除后把观察期间挂起的
 * 未审核（OPEN）日报按各自冻结的原规则版本统一补算，已审核日报保持冻结。
 */
@RestController
@RequestMapping("/api/aqua/sensor/risk-watch")
public class SensorRiskWatchController {

    private final SensorRiskWatchService riskWatchService;
    private final SensorDailyReportService dailyReportService;

    public SensorRiskWatchController(SensorRiskWatchService riskWatchService,
                                     SensorDailyReportService dailyReportService) {
        this.riskWatchService = riskWatchService;
        this.dailyReportService = dailyReportService;
    }

    @GetMapping
    public ApiResponse<SensorRiskWatch> get() {
        return ApiResponse.ok(riskWatchService.getWatch());
    }

    /** 置为高风险观察：离线归并与日报汇总同步限制 */
    @PostMapping("/watch-on")
    public ApiResponse<SensorRiskWatch> watchOn(@Validated @RequestBody SensorRiskWatchToggleRequest request) {
        return ApiResponse.ok(riskWatchService.watchOn(request));
    }

    /** 解除高风险观察：恢复离线归并，并按原规则版本补算全部未审核日报 */
    @PostMapping("/watch-off")
    public ApiResponse<Map<String, Object>> watchOff(@Validated @RequestBody SensorRiskWatchToggleRequest request) {
        SensorRiskWatch watch = riskWatchService.watchOff(request);
        int recalcCount = dailyReportService.recalcAllOpenAfterWatchOff(request.getRequestId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watch", watch);
        result.put("openReportRecalcCount", recalcCount);
        return ApiResponse.ok(result);
    }
}
