package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.DowntimeWindowRequest;
import com.evops.aqua.maint.entity.DowntimeWindow;
import com.evops.aqua.maint.service.DowntimeWindowService;
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

/**
 * 停机窗口：设备允许停机的半开区间，工单必须落入窗口。
 */
@RestController
@RequestMapping("/api/aqua/maint")
public class DowntimeWindowController {

    private final DowntimeWindowService windowService;

    public DowntimeWindowController(DowntimeWindowService windowService) {
        this.windowService = windowService;
    }

    @PostMapping("/devices/{deviceId}/windows")
    public ApiResponse<DowntimeWindow> register(@PathVariable Long deviceId,
                                                @Validated @RequestBody DowntimeWindowRequest request) {
        return ApiResponse.ok(windowService.register(deviceId, request));
    }

    @GetMapping("/windows")
    public ApiResponse<List<DowntimeWindow>> list(@RequestParam(required = false) Long deviceId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(windowService.list(deviceId, status));
    }

    @GetMapping("/windows/{id}")
    public ApiResponse<DowntimeWindow> detail(@PathVariable Long id) {
        return ApiResponse.ok(windowService.getOrThrow(id));
    }
}
