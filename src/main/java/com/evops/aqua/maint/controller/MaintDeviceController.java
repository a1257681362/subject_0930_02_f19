package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.MaintDeviceRegisterRequest;
import com.evops.aqua.maint.dto.InitialInstallRequest;
import com.evops.aqua.maint.dto.MeterReadingRequest;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintMeterReading;
import com.evops.aqua.maint.entity.SparePart;
import com.evops.aqua.maint.service.MaintDeviceService;
import com.evops.aqua.maint.service.SpareService;
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
 * 养殖设备档案与运行小时账：增氧机/泵站/投饵机。
 */
@RestController
@RequestMapping("/api/aqua/maint/devices")
public class MaintDeviceController {

    private final MaintDeviceService deviceService;
    private final SpareService spareService;

    public MaintDeviceController(MaintDeviceService deviceService, SpareService spareService) {
        this.deviceService = deviceService;
        this.spareService = spareService;
    }

    @PostMapping
    public ApiResponse<MaintDevice> register(@Validated @RequestBody MaintDeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    @GetMapping
    public ApiResponse<List<MaintDevice>> list(@RequestParam(required = false) String deviceType,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(deviceService.list(deviceType, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaintDevice> detail(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getOrThrow(id));
    }

    /** 运行小时上报（只追加，requestId 幂等，读数不得回退）。 */
    @PostMapping("/{id}/meter-readings")
    public ApiResponse<MaintMeterReading> reportReading(@PathVariable Long id,
                                                        @Validated @RequestBody MeterReadingRequest request) {
        return ApiResponse.ok(deviceService.reportReading(id, request));
    }

    /** 初始装机登记：在库备件装到设备，形成序列号基线，后续换件时即旧件序列号。 */
    @PostMapping("/{id}/installed-parts")
    public ApiResponse<SparePart> installInitial(@PathVariable Long id,
                                                 @Validated @RequestBody InitialInstallRequest request) {
        MaintDevice device = deviceService.getOrThrow(id);
        return ApiResponse.ok(spareService.installInitial(
                device, request.getSerialNo(), request.getOperator(), request.getRemark()));
    }
}
