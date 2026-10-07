package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.PostponeRequest;
import com.evops.aqua.tide.dto.RescheduleRequest;
import com.evops.aqua.tide.dto.ScheduleCreateRequest;
import com.evops.aqua.tide.dto.SchedulePublishRequest;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.service.TideScheduleService;
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
 * 排班：窗口 + 池塘 + 设备。同一设备同一时间只能服务一口池。
 * 发布后修改必须经调度审批；自动顺延生成新计划行，原计划不变。
 */
@RestController
@RequestMapping("/api/aqua/tide/schedules")
public class TideScheduleController {

    private final TideScheduleService scheduleService;

    public TideScheduleController(TideScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 创建排班（requestId 幂等）：校验窗口开放、海区一致、设备一口池。 */
    @PostMapping
    public ApiResponse<TideSchedule> create(@Validated @RequestBody ScheduleCreateRequest request) {
        return ApiResponse.ok(scheduleService.create(request));
    }

    @GetMapping
    public ApiResponse<List<TideSchedule>> list(@RequestParam(required = false) Long pondId,
                                                @RequestParam(required = false) Long deviceId,
                                                @RequestParam(required = false) Long windowId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(scheduleService.list(pondId, deviceId, windowId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TideSchedule> detail(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.getOrThrow(id));
    }

    /** 发布排班：发布后再修改必须经调度审批；重复发布幂等。 */
    @PostMapping("/{id}/publish")
    public ApiResponse<TideSchedule> publish(@PathVariable Long id,
                                             @Validated @RequestBody SchedulePublishRequest request) {
        return ApiResponse.ok(scheduleService.publish(id, request.getOperator()));
    }

    /** 改期：待发布直接改；已发布必须带调度审批人与审批意见。 */
    @PostMapping("/{id}/reschedule")
    public ApiResponse<TideSchedule> reschedule(@PathVariable Long id,
                                                @Validated @RequestBody RescheduleRequest request) {
        return ApiResponse.ok(scheduleService.reschedule(id, request));
    }

    /** 自动顺延：系统挑下一个设备空闲窗口生成新排班，原计划行不变。 */
    @PostMapping("/{id}/postpone")
    public ApiResponse<TideSchedule> postpone(@PathVariable Long id,
                                              @Validated @RequestBody PostponeRequest request) {
        return ApiResponse.ok(scheduleService.postpone(id, request));
    }
}
