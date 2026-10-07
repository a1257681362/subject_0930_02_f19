package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.DeviceRequest;
import com.evops.aqua.tide.dto.ShiftRequest;
import com.evops.aqua.tide.dto.TideEventRequest;
import com.evops.aqua.tide.dto.WeatherBanRequest;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideDevice;
import com.evops.aqua.tide.entity.TideEvent;
import com.evops.aqua.tide.entity.TideWeatherBan;
import com.evops.aqua.tide.service.TideTableService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 潮汐基础数据：潮汐表、禁作天气、人员班次、换水设备的登记与查询。
 */
@RestController
@RequestMapping("/api/aqua/tide")
public class TideTableController {

    private final TideTableService tableService;

    public TideTableController(TideTableService tableService) {
        this.tableService = tableService;
    }

    /** 登记潮汐事件（requestId 幂等；同海区同时刻同潮型自然键幂等）。 */
    @PostMapping("/events")
    public ApiResponse<TideEvent> registerEvent(@Validated @RequestBody TideEventRequest request) {
        return ApiResponse.ok(tableService.registerEvent(request));
    }

    @GetMapping("/events")
    public ApiResponse<List<TideEvent>> listEvents(@RequestParam(required = false) String seaArea) {
        return ApiResponse.ok(tableService.listEvents(seaArea));
    }

    /** 登记禁作天气（requestId 幂等）。 */
    @PostMapping("/weather-bans")
    public ApiResponse<TideWeatherBan> registerBan(@Validated @RequestBody WeatherBanRequest request) {
        return ApiResponse.ok(tableService.registerBan(request));
    }

    @GetMapping("/weather-bans")
    public ApiResponse<List<TideWeatherBan>> listBans(@RequestParam(required = false) String seaArea) {
        return ApiResponse.ok(tableService.listBans(seaArea));
    }

    /** 登记人员班次（requestId 幂等）。 */
    @PostMapping("/shifts")
    public ApiResponse<TideCrewShift> registerShift(@Validated @RequestBody ShiftRequest request) {
        return ApiResponse.ok(tableService.registerShift(request));
    }

    @GetMapping("/shifts")
    public ApiResponse<List<TideCrewShift>> listShifts(@RequestParam(required = false) String crewName) {
        return ApiResponse.ok(tableService.listShifts(crewName));
    }

    /** 登记换水设备（设备编号唯一，重复登记返回已有设备）。 */
    @PostMapping("/devices")
    public ApiResponse<TideDevice> registerDevice(@Validated @RequestBody DeviceRequest request) {
        return ApiResponse.ok(tableService.registerDevice(request));
    }

    @GetMapping("/devices")
    public ApiResponse<List<TideDevice>> listDevices(@RequestParam(required = false) String seaArea) {
        return ApiResponse.ok(tableService.listDevices(seaArea));
    }
}
