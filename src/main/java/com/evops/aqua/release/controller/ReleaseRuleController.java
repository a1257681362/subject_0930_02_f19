package com.evops.aqua.release.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.release.dto.ReleaseRulePublishRequest;
import com.evops.aqua.release.entity.ReleaseRuleVersion;
import com.evops.aqua.release.entity.ReleaseThresholdRule;
import com.evops.aqua.release.mapper.ReleaseRuleVersionMapper;
import com.evops.aqua.release.service.ReleaseRuleVersionService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出池检验阈值规则版本：检验项目按「品种 + 用途」组合配置。
 * 发布新版本后旧版本停用；已建立检验单冻结建单时版本。
 */
@RestController
@RequestMapping("/api/aqua/release/rule-versions")
public class ReleaseRuleController {

    private final ReleaseRuleVersionService ruleVersionService;
    private final ReleaseRuleVersionMapper versionMapper;

    public ReleaseRuleController(ReleaseRuleVersionService ruleVersionService,
                                 ReleaseRuleVersionMapper versionMapper) {
        this.ruleVersionService = ruleVersionService;
        this.versionMapper = versionMapper;
    }

    @PostMapping
    public ApiResponse<ReleaseRuleVersion> publish(@Validated @RequestBody ReleaseRulePublishRequest request) {
        return ApiResponse.ok(ruleVersionService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<ReleaseRuleVersion>> list() {
        return ApiResponse.ok(versionMapper.selectList(
                new QueryWrapper<ReleaseRuleVersion>().orderByDesc("id")));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        ReleaseRuleVersion version = ruleVersionService.getOrThrow(id);
        List<ReleaseThresholdRule> thresholds = ruleVersionService.listThresholds(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", version);
        detail.put("thresholds", thresholds);
        detail.put("combos", ruleVersionService.combosOf(id));
        return ApiResponse.ok(detail);
    }
}
