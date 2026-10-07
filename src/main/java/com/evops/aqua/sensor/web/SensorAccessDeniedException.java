package com.evops.aqua.sensor.web;

/**
 * 离线归并高风险观察准入异常：观察期间离线补传归并/日报汇总被拒绝，
 * 统一以 403 返回（区别于参数非法的业务拒绝），拒绝事实已以独立事务留痕。
 */
public class SensorAccessDeniedException extends RuntimeException {

    public SensorAccessDeniedException(String message) {
        super(message);
    }
}
