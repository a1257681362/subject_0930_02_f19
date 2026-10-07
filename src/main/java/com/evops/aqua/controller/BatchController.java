package com.evops.aqua.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.dto.BatchStockRequest;
import com.evops.aqua.dto.BiomassUpdateRequest;
import com.evops.aqua.dto.HarvestRequest;
import com.evops.aqua.dto.TransferRequest;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.entity.TransferRecord;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.TransferRecordMapper;
import com.evops.aqua.service.LedgerRecordService;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投苗批次（原始投苗单）。
 * 原始投苗单不可删除：本控制器不提供 DELETE 接口，数据库表也不设删除标记。
 */
@RestController
@RequestMapping("/api/aqua/batches")
public class BatchController {

    private final SeedlingBatchService batchService;
    private final PondService pondService;
    private final LedgerRecordService ledgerRecordService;
    private final TransferRecordMapper transferRecordMapper;
    private final HarvestRecordMapper harvestRecordMapper;

    public BatchController(SeedlingBatchService batchService,
                           PondService pondService,
                           LedgerRecordService ledgerRecordService,
                           TransferRecordMapper transferRecordMapper,
                           HarvestRecordMapper harvestRecordMapper) {
        this.batchService = batchService;
        this.pondService = pondService;
        this.ledgerRecordService = ledgerRecordService;
        this.transferRecordMapper = transferRecordMapper;
        this.harvestRecordMapper = harvestRecordMapper;
    }

    @PostMapping
    public ApiResponse<SeedlingBatch> stock(@Validated @RequestBody BatchStockRequest request) {
        return ApiResponse.ok(batchService.stock(request));
    }

    @GetMapping
    public ApiResponse<List<SeedlingBatch>> list(@RequestParam(required = false) Long pondId) {
        QueryWrapper<SeedlingBatch> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.orderByAsc("id");
        return ApiResponse.ok(batchService.list(wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<SeedlingBatch> detail(@PathVariable Long id) {
        return ApiResponse.ok(batchService.getOrThrow(id));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<TransferRecord> transfer(@PathVariable Long id,
                                                @Validated @RequestBody TransferRequest request) {
        return ApiResponse.ok(batchService.transfer(id, request));
    }

    @PostMapping("/{id}/harvest")
    public ApiResponse<HarvestRecord> harvest(@PathVariable Long id,
                                              @Validated @RequestBody HarvestRequest request) {
        return ApiResponse.ok(batchService.harvest(id, request));
    }

    /** 核定批次当前估计生物量，作为后续转池成本分配的生物量权重。 */
    @PostMapping("/{id}/biomass")
    public ApiResponse<SeedlingBatch> updateBiomass(@PathVariable Long id,
                                                    @Validated @RequestBody BiomassUpdateRequest request) {
        return ApiResponse.ok(batchService.updateBiomass(id, request));
    }

    /**
     * 批次全链路追溯：投苗单 + 换池 + 日常/饲料/用药/抽检 + 出池。
     */
    @GetMapping("/{id}/trace")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        SeedlingBatch batch = batchService.getOrThrow(id);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("batch", batch);
        trace.put("pond", pondService.getOrThrow(batch.getPondId()));
        trace.put("transfers", transferRecordMapper.selectList(
                new QueryWrapper<TransferRecord>().eq("batch_id", id).orderByAsc("id")));
        trace.put("dailyRecords", ledgerRecordService.listDaily(null, id));
        trace.put("feedRecords", ledgerRecordService.listFeed(null, id));
        trace.put("medicationRecords", ledgerRecordService.listMedication(null, id));
        trace.put("inspectionRecords", ledgerRecordService.listInspection(null, id));
        trace.put("harvestRecords", harvestRecordMapper.selectList(
                new QueryWrapper<HarvestRecord>().eq("batch_id", id).orderByAsc("id")));
        return ApiResponse.ok(trace);
    }
}
