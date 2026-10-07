package com.evops.aqua.wq.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.wq.dto.RuleVersionPublishRequest;
import com.evops.aqua.wq.entity.WqRuleVersion;
import com.evops.aqua.wq.entity.WqStageRule;
import com.evops.aqua.wq.mapper.WqRuleVersionMapper;
import com.evops.aqua.wq.service.WqRuleVersionService;
import com.evops.aqua.wq.service.WqTaskService;
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
 * 水质指标规则版本：按养殖阶段配置采样频率与指标正常范围。
 * 发布新版本后旧版本停用；新版本只重绑未开始任务（已开始任务冻结旧版本）。
 */
@RestController
@RequestMapping("/api/aqua/wq/rule-versions")
public class WqRuleVersionController {

    private final WqRuleVersionService ruleVersionService;
    private final WqRuleVersionMapper versionMapper;
    private final WqTaskService taskService;

    public WqRuleVersionController(WqRuleVersionService ruleVersionService,
                                   WqRuleVersionMapper versionMapper,
                                   WqTaskService taskService) {
        this.ruleVersionService = ruleVersionService;
        this.versionMapper = versionMapper;
        this.taskService = taskService;
    }

    @PostMapping
    public ApiResponse<WqRuleVersion> publish(@Validated @RequestBody RuleVersionPublishRequest request) {
        // 发布与未开始任务重绑在同一事务内完成
        return ApiResponse.ok(ruleVersionService.publish(request, taskService::rebindUnstartedTasks));
    }

    @GetMapping
    public ApiResponse<List<WqRuleVersion>> list() {
        return ApiResponse.ok(versionMapper.selectList(
                new QueryWrapper<WqRuleVersion>().orderByDesc("id")));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        WqRuleVersion version = ruleVersionService.getOrThrow(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", version);
        List<WqStageRule> stages = ruleVersionService.listStages(id);
        List<Map<String, Object>> stagePayload = new java.util.ArrayList<>();
        for (WqStageRule stage : stages) {
            Map<String, Object> stageMap = new LinkedHashMap<>();
            stageMap.put("stage", stage);
            stageMap.put("indicators", ruleVersionService.listIndicators(stage.getId()));
            stagePayload.add(stageMap);
        }
        detail.put("stages", stagePayload);
        return ApiResponse.ok(detail);
    }
}
