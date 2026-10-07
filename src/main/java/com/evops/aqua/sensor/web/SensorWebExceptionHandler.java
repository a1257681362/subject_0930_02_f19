package com.evops.aqua.sensor.web;

import com.evops.common.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 传感器模块准入异常统一以 403 + ApiResponse.fail 返回，
 * 不落到全局兜底的“系统处理失败”，让高风险观察限制语义明确。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SensorWebExceptionHandler {

    @ExceptionHandler(SensorAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(SensorAccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiResponse.fail(ex.getMessage()));
    }
}
