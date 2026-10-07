package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.SensorRuleVersionPublishRequest;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.service.SensorRuleVersionService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 归并判定规则版本：盐度/溶氧/温度正常范围。
 * 规则调整只能发布新版本，旧版本自动停用；已归并结论不回改。
 */
@RestController
@RequestMapping("/api/aqua/sensor/rule-versions")
public class SensorRuleVersionController {

    private final SensorRuleVersionService ruleVersionService;

    public SensorRuleVersionController(SensorRuleVersionService ruleVersionService) {
        this.ruleVersionService = ruleVersionService;
    }

    /** 发布新版本：旧 ACTIVE 版本自动停用，仅作用于发布后的归并判定 */
    @PostMapping
    public ApiResponse<SensorRuleVersion> publish(@Validated @RequestBody SensorRuleVersionPublishRequest request) {
        return ApiResponse.ok(ruleVersionService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<SensorRuleVersion>> list() {
        return ApiResponse.ok(ruleVersionService.listAll());
    }
}
