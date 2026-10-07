package com.evops.aqua.report.config;

import com.evops.aqua.report.service.ReportAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 口径管理缺权过滤器注册：
 * - 以 bean 名 caliberAccess 暴露给 Shiro 过滤链（shiro-spring-boot-web-starter 自动收集
 *   容器内 Filter bean，按 bean 名在链中引用）；
 * - 同时禁止 Spring Boot 把它再注册到全局 Servlet 过滤链（只允许经 Shiro 链在
 *   /api/aqua/report/calibers/** 上生效）。
 */
@Configuration
public class ReportFilterConfig {

    @Bean(name = "caliberAccess")
    public CaliberAccessFilter caliberAccessFilter(ReportAccessService accessService,
                                                   ObjectMapper objectMapper) {
        return new CaliberAccessFilter(accessService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<CaliberAccessFilter> caliberAccessFilterRegistration(
            CaliberAccessFilter filter) {
        FilterRegistrationBean<CaliberAccessFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
