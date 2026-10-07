package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.LoadRecordRequest;
import com.evops.aqua.fishing.dto.ReservationCompleteRequest;
import com.evops.aqua.fishing.dto.ReservationLockRequest;
import com.evops.aqua.fishing.dto.VehicleChangeRequest;
import com.evops.aqua.fishing.entity.FishingLoad;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.entity.FishingVehicleChange;
import com.evops.aqua.fishing.service.FishingReservationService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 捕捞预约入口：锁定（占用库位）、现场分仓（按实际规格判级入仓）、
 * 完成（短装/超装生成差异单）、临时换车（登记原因并触发客户通知）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/reservations")
public class FishingReservationController {

    private final FishingReservationService reservationService;

    public FishingReservationController(FishingReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{id}")
    public ApiResponse<FishingReservation> view(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.getOrThrow(id));
    }

    @PostMapping("/{id}/lock")
    public ApiResponse<FishingReservation> lock(@PathVariable Long id,
                                                @Validated @RequestBody ReservationLockRequest request) {
        return ApiResponse.ok(reservationService.lock(id, request));
    }

    @PostMapping("/{id}/loads")
    public ApiResponse<FishingLoad> recordLoad(@PathVariable Long id,
                                               @Validated @RequestBody LoadRecordRequest request) {
        return ApiResponse.ok(reservationService.recordLoad(id, request));
    }

    @GetMapping("/{id}/loads")
    public ApiResponse<List<FishingLoad>> loads(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.loadsOf(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<FishingReservation> complete(@PathVariable Long id,
                                                    @Validated @RequestBody ReservationCompleteRequest request) {
        return ApiResponse.ok(reservationService.complete(id, request));
    }

    @PostMapping("/{id}/vehicle-change")
    public ApiResponse<FishingVehicleChange> changeVehicle(@PathVariable Long id,
                                                           @Validated @RequestBody VehicleChangeRequest request) {
        return ApiResponse.ok(reservationService.changeVehicle(id, request));
    }
}
