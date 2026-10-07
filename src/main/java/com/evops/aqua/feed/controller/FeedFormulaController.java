package com.evops.aqua.feed.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.dto.FormulaPublishRequest;
import com.evops.aqua.feed.entity.FeedFormulaRule;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.mapper.FeedFormulaVersionMapper;
import com.evops.aqua.feed.service.FeedFormulaService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投喂配方版本：按苗种发布，版本下按日龄/水温区间配置饲料与计划量。
 * 配方调整产生新版本，旧版本停用；历史投喂单继续引用其冻结的旧版本快照。
 */
@RestController
@RequestMapping("/api/aqua/feed/formulas")
public class FeedFormulaController {

    private final FeedFormulaService formulaService;
    private final FeedFormulaVersionMapper versionMapper;

    public FeedFormulaController(FeedFormulaService formulaService,
                                 FeedFormulaVersionMapper versionMapper) {
        this.formulaService = formulaService;
        this.versionMapper = versionMapper;
    }

    @PostMapping
    public ApiResponse<FeedFormulaVersion> publish(@Validated @RequestBody FormulaPublishRequest request) {
        return ApiResponse.ok(formulaService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<FeedFormulaVersion>> list(@RequestParam(required = false) String species) {
        QueryWrapper<FeedFormulaVersion> wrapper = new QueryWrapper<>();
        wrapper.eq(species != null && !species.trim().isEmpty(), "species", species);
        wrapper.orderByDesc("id");
        return ApiResponse.ok(versionMapper.selectList(wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        FeedFormulaVersion version = formulaService.getOrThrow(id);
        List<FeedFormulaRule> rules = formulaService.listRules(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", version);
        detail.put("rules", rules);
        return ApiResponse.ok(detail);
    }
}
