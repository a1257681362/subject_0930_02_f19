package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.GradingRulePublishRequest;
import com.evops.aqua.fishing.entity.FishingGradingRule;
import com.evops.aqua.fishing.entity.FishingGradingRuleLine;
import com.evops.aqua.fishing.service.FishingGradingRuleService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分级规则版本入口：发布（新版本自动停用旧版本）、版本与明细查询。
 */
@RestController
@RequestMapping("/api/aqua/fishing/grading-rules")
public class FishingGradingRuleController {

    private final FishingGradingRuleService ruleService;

    public FishingGradingRuleController(FishingGradingRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ApiResponse<FishingGradingRule> publish(@Validated @RequestBody GradingRulePublishRequest request) {
        return ApiResponse.ok(ruleService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<FishingGradingRule>> list() {
        return ApiResponse.ok(ruleService.list());
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<FishingGradingRuleLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.linesOf(id));
    }
}
