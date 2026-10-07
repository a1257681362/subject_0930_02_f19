package com.evops.aqua.fulfill.external;

/**
 * 海水养殖管理外部依赖不可用：连接失败/超时/非成功响应均抛此异常。
 * 仅在履约模块内被 Controller 与发件箱扫描捕获，不进入全局异常兜底。
 */
public class ExternalUnavailableException extends RuntimeException {

    public ExternalUnavailableException(String message) {
        super(message);
    }

    public ExternalUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
