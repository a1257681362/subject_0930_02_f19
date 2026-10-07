package com.evops.aqua.noculture.controller;

import com.evops.aqua.noculture.dto.RuleCreateRequest;
import com.evops.aqua.noculture.dto.RuleRevokeRequest;
import com.evops.aqua.noculture.entity.NoCultureCompensation;
import com.evops.aqua.noculture.entity.NoCultureFreeze;
import com.evops.aqua.noculture.entity.NoCultureMatch;
import com.evops.aqua.noculture.entity.NoCultureRule;
import com.evops.aqua.noculture.service.NoCultureRuleService;
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
import java.util.Map;

/**
 * 海区禁养规则维护：监管人员维护禁养范围、生效时间、依据文件；
 * 登记即冻结空间命中的在途批次；撤销逐冻结账补偿，仍被他规冻结的保持冻结。
 * 规则不提供删除接口。
 */
@RestController
@RequestMapping("/api/aqua/noculture/rules")
public class NoCultureRuleController {

    private final NoCultureRuleService ruleService;

    public NoCultureRuleController(NoCultureRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Validated @RequestBody RuleCreateRequest request) {
        return ApiResponse.ok(ruleService.create(request));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<Map<String, Object>> revoke(@PathVariable Long id,
                                                   @Validated @RequestBody RuleRevokeRequest request) {
        return ApiResponse.ok(ruleService.revoke(id, request));
    }

    @GetMapping
    public ApiResponse<List<NoCultureRule>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(ruleService.listRules(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoCultureRule> detail(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.getOrThrow(id));
    }

    @GetMapping("/{id}/matches")
    public ApiResponse<List<NoCultureMatch>> matches(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.listMatches(id, null));
    }

    @GetMapping("/{id}/freezes")
    public ApiResponse<List<NoCultureFreeze>> freezes(@PathVariable Long id,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(ruleService.listFreezes(id, null, status));
    }

    @GetMapping("/{id}/compensations")
    public ApiResponse<List<NoCultureCompensation>> compensations(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.listCompensations(id, null));
    }

    /** 勾稽核对：禁养区域→空间匹配→冻结账→撤销补偿逐条对应，无悬挂。 */
    @GetMapping("/{id}/reconcile")
    public ApiResponse<Map<String, Object>> reconcile(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.reconcile(id));
    }
}
