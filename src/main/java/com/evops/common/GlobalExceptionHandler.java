package com.evops.common;

import org.apache.shiro.authc.AuthenticationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        // 一次性指出全部缺项/非法项，便于调用方补齐后只重试当前步骤（按字段去重、保留首次信息）。
        Map<String, String> missing = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            missing.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        if (missing.isEmpty()) {
            return ApiResponse.fail("参数校验失败：参数不合法");
        }
        StringBuilder detail = new StringBuilder("参数校验失败，缺项/非法项共").append(missing.size()).append("项：");
        boolean first = true;
        for (Map.Entry<String, String> entry : missing.entrySet()) {
            if (!first) {
                detail.append("；");
            }
            first = false;
            detail.append('[').append(entry.getKey()).append(']').append(entry.getValue());
        }
        return ApiResponse.fail(detail.toString());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException ex) {
        return ApiResponse.fail("编号已存在，唯一性约束冲突");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ApiResponse.fail("该资源不支持 " + ex.getMethod() + " 操作");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ApiResponse<Void> handleAuthentication(AuthenticationException ex) {
        return ApiResponse.fail("用户名或密码错误");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        return ApiResponse.fail("系统处理失败");
    }
}
