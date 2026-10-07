package com.evops.aqua.report.web;

/**
 * 监管月报准入拒绝异常：既用于海水养殖管理缺权时的口径管理入口拒绝，
 * 也用于海区数据权限越权。由入口过滤链或服务闸门抛出，统一返回 403。
 */
public class ReportAccessDeniedException extends RuntimeException {

    public ReportAccessDeniedException(String message) {
        super(message);
    }
}
