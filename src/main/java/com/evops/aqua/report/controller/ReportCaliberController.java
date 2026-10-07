package com.evops.aqua.report.controller;

import com.evops.aqua.report.dto.CaliberPublishRequest;
import com.evops.aqua.report.entity.ReportCaliberIndicator;
import com.evops.aqua.report.entity.ReportCaliberVersion;
import com.evops.aqua.report.service.CaliberVersionService;
import com.evops.aqua.report.service.ReportAccessService;
import com.evops.aqua.report.service.ReportEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水产监管月报口径管理。
 * 入口已由 Shiro 链 caliberAccess 过滤器拦截；此处服务闸门与 URL 无关，
 * 即使绕过/更换调用地址到达本逻辑，海水养殖管理缺权时同样拒绝。
 */
@RestController
@RequestMapping("/api/aqua/report/calibers")
public class ReportCaliberController {

    private final CaliberVersionService caliberService;
    private final ReportAccessService accessService;

    public ReportCaliberController(CaliberVersionService caliberService,
                                   ReportAccessService accessService) {
        this.caliberService = caliberService;
        this.accessService = accessService;
    }

    /** 发布新口径版本：旧版本冻结，新版本只作用于之后生成的月报。 */
    @PostMapping
    public ApiResponse<ReportCaliberVersion> publish(@RequestBody(required = false) CaliberPublishRequest request,
                                                     HttpServletRequest httpRequest) {
        accessService.requireCaliberManageAllowed(httpRequest.getRequestURI(),
                request == null ? null : request.getRequestId(),
                ReportEvidenceService.currentOperator());
        return ApiResponse.ok(caliberService.publish(request, ReportEvidenceService.currentOperator()));
    }

    /** 口径版本列表（只读，不触发缺权闸门）。 */
    @GetMapping
    public ApiResponse<List<ReportCaliberVersion>> list() {
        return ApiResponse.ok(caliberService.listVersions());
    }

    /** 版本详情：逐指标固化口径。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        ReportCaliberVersion version = caliberService.requireVersion(id);
        List<ReportCaliberIndicator> indicators = caliberService.listIndicators(id);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("version", version);
        view.put("indicators", indicators);
        return ApiResponse.ok(view);
    }
}
