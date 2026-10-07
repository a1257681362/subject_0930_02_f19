package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.DeviceRegisterRequest;
import com.evops.aqua.sensor.dto.DeviceReplaceRequest;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.service.SensorDeviceService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 传感器设备档案：登记（幂等）、设备更换建立新旧关联、谱系追溯。
 */
@RestController
@RequestMapping("/api/aqua/sensor/devices")
public class SensorDeviceController {

    private final SensorDeviceService deviceService;

    public SensorDeviceController(SensorDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /** 登记设备：requestId 幂等，重复登记返回首次记录 */
    @PostMapping
    public ApiResponse<SensorDevice> register(@Validated @RequestBody DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    @GetMapping
    public ApiResponse<List<SensorDevice>> list(@RequestParam(required = false) Long pondId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(deviceService.listDevices(pondId, status));
    }

    /** 设备更换：新设备接续同池，旧设备退役，建立新旧关联；requestId 幂等 */
    @PostMapping("/{id}/replace")
    public ApiResponse<Map<String, Object>> replace(@PathVariable Long id,
                                                    @Validated @RequestBody DeviceReplaceRequest request) {
        return ApiResponse.ok(deviceService.replace(id, request));
    }

    /** 设备谱系：沿新旧关联链双向追溯 */
    @GetMapping("/{id}/lineage")
    public ApiResponse<List<Map<String, Object>>> lineage(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.lineage(id));
    }
}
