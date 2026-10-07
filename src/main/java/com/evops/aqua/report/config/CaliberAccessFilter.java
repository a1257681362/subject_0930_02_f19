package com.evops.aqua.report.config;

import com.evops.aqua.report.service.ReportAccessService;
import com.evops.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.SecurityUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 「水产监管月报口径管理」入口过滤链闸门（bean 名 caliberAccess，挂在 Shiro 过滤链最前）：
 * 海水养殖管理权限收紧（缺权）时，对 /api/aqua/report/calibers/** 的请求在进入控制器之前
 * 直接返回 403，并以独立事务留拒绝证据。控制器内另有与 URL 无关的第二道闸门——
 * 即使绕过/更换调用地址到达服务逻辑同样拒绝。
 */
public class CaliberAccessFilter extends OncePerRequestFilter {

    private final ReportAccessService accessService;
    private final ObjectMapper objectMapper;

    public CaliberAccessFilter(ReportAccessService accessService, ObjectMapper objectMapper) {
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws javax.servlet.ServletException,
            java.io.IOException {
        if (accessService.isCaliberManageAllowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        String requestId = request.getHeader("X-Request-Id");
        String operator = currentPrincipal();
        accessService.recordEntryDenied(request.getRequestURI(), requestId, operator);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        try {
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail("海水养殖管理权限已收紧（缺权），水产监管月报口径管理入口已拒绝"));
        } catch (Exception ignore) {
            response.getWriter().write("{\"success\":false,\"message\":\"口径管理入口已拒绝\"}");
        }
    }

    private String currentPrincipal() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            return principal == null ? "anonymous" : String.valueOf(principal);
        } catch (Exception ex) {
            return "anonymous";
        }
    }
}
