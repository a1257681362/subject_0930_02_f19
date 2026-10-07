package com.evops.aqua.controller;

import com.evops.aqua.dto.DailyRecordRequest;
import com.evops.aqua.dto.FeedRecordRequest;
import com.evops.aqua.dto.InspectionRecordRequest;
import com.evops.aqua.dto.MedicationRecordRequest;
import com.evops.aqua.entity.DailyRecord;
import com.evops.aqua.entity.FeedRecord;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.MedicationRecord;
import com.evops.aqua.service.LedgerRecordService;
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
 * 养殖过程台账：日常记录、饲料、用药、抽检，按池按批留痕。
 */
@RestController
@RequestMapping("/api/aqua/records")
public class LedgerRecordController {

    private final LedgerRecordService ledgerRecordService;

    public LedgerRecordController(LedgerRecordService ledgerRecordService) {
        this.ledgerRecordService = ledgerRecordService;
    }

    @PostMapping("/daily")
    public ApiResponse<DailyRecord> addDaily(@Validated @RequestBody DailyRecordRequest request) {
        return ApiResponse.ok(ledgerRecordService.addDaily(request));
    }

    @GetMapping("/daily")
    public ApiResponse<List<DailyRecord>> listDaily(@RequestParam(required = false) Long pondId,
                                                    @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(ledgerRecordService.listDaily(pondId, batchId));
    }

    @PostMapping("/feed")
    public ApiResponse<FeedRecord> addFeed(@Validated @RequestBody FeedRecordRequest request) {
        return ApiResponse.ok(ledgerRecordService.addFeed(request));
    }

    @GetMapping("/feed")
    public ApiResponse<List<FeedRecord>> listFeed(@RequestParam(required = false) Long pondId,
                                                  @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(ledgerRecordService.listFeed(pondId, batchId));
    }

    @PostMapping("/medication")
    public ApiResponse<MedicationRecord> addMedication(@Validated @RequestBody MedicationRecordRequest request) {
        return ApiResponse.ok(ledgerRecordService.addMedication(request));
    }

    @GetMapping("/medication")
    public ApiResponse<List<MedicationRecord>> listMedication(@RequestParam(required = false) Long pondId,
                                                              @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(ledgerRecordService.listMedication(pondId, batchId));
    }

    @PostMapping("/inspection")
    public ApiResponse<InspectionRecord> addInspection(@Validated @RequestBody InspectionRecordRequest request) {
        return ApiResponse.ok(ledgerRecordService.addInspection(request));
    }

    @GetMapping("/inspection")
    public ApiResponse<List<InspectionRecord>> listInspection(@RequestParam(required = false) Long pondId,
                                                              @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(ledgerRecordService.listInspection(pondId, batchId));
    }
}
