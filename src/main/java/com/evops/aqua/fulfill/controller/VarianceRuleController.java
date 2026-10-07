package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.VarianceRulePublishRequest;
import com.evops.aqua.fulfill.entity.VarianceRule;
import com.evops.aqua.fulfill.entity.VarianceRuleLine;
import com.evops.aqua.fulfill.service.VarianceRuleService;
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
 * 净重差异规则版本入口：发布（新版本自动停用旧版本）、版本与明细查询。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/variance-rules")
public class VarianceRuleController {

    private final VarianceRuleService ruleService;

    public VarianceRuleController(VarianceRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ApiResponse<VarianceRule> publish(@Validated @RequestBody VarianceRulePublishRequest request) {
        return ApiResponse.ok(ruleService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<VarianceRule>> list() {
        return ApiResponse.ok(ruleService.list());
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<VarianceRuleLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.linesOf(id));
    }
}
