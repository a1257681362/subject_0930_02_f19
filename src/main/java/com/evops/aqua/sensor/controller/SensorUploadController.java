package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.entity.SensorQuarantine;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.service.SensorUploadService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 设备离线批量上传：原始报文整体接收，先验签后归并。
 * 签名 = HMAC-SHA256(设备密钥, requestId + "\n" + 原始报文) 的十六进制串；
 * 验签失败/未知设备整包隔离并保留原文。
 */
@RestController
@RequestMapping("/api/aqua/sensor")
public class SensorUploadController {

    private final SensorUploadService uploadService;

    public SensorUploadController(SensorUploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** 批量上传：按设备时间与序号归并；requestId 整包幂等 */
    @PostMapping("/uploads")
    public ApiResponse<Map<String, Object>> upload(@RequestHeader("X-Device-Code") String deviceCode,
                                                   @RequestHeader("X-Request-Id") String requestId,
                                                   @RequestHeader(value = "X-Signature", required = false) String signature,
                                                   @RequestBody String rawBody) {
        return ApiResponse.ok(uploadService.upload(deviceCode, requestId, signature, rawBody));
    }

    /** 归并读数查询：可按池/指标/状态过滤 */
    @GetMapping("/readings")
    public ApiResponse<List<SensorReading>> readings(@RequestParam(required = false) Long pondId,
                                                     @RequestParam(required = false) String metricType,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.ok(uploadService.listReadings(pondId, metricType, status));
    }

    /** 隔离区：签名失败/未知设备的上传包，保留原始报文 */
    @GetMapping("/quarantine")
    public ApiResponse<List<SensorQuarantine>> quarantine() {
        return ApiResponse.ok(uploadService.listQuarantine());
    }
}
