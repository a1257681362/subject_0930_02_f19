package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.dto.AccessPolicyToggleRequest;
import com.evops.aqua.feed.entity.FeedAccessPolicy;
import com.evops.aqua.feed.service.FeedAccessPolicyService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 海水养殖管理权限开关：收紧后饲料批号停止推进，已确认的配方版本与历史投喂事实保留。
 */
@RestController
@RequestMapping("/api/aqua/feed/access")
public class FeedAccessController {

    private final FeedAccessPolicyService policyService;

    public FeedAccessController(FeedAccessPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ApiResponse<FeedAccessPolicy> get() {
        return ApiResponse.ok(policyService.getPolicy());
    }

    @PostMapping("/tighten")
    public ApiResponse<FeedAccessPolicy> tighten(@Validated @RequestBody AccessPolicyToggleRequest request) {
        return ApiResponse.ok(policyService.tighten(request));
    }

    @PostMapping("/relax")
    public ApiResponse<FeedAccessPolicy> relax(@Validated @RequestBody AccessPolicyToggleRequest request) {
        return ApiResponse.ok(policyService.relax(request));
    }
}
