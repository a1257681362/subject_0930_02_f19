package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.entity.CostBalance;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.mapper.CostBalanceMapper;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本分摊与批次结转报表。
 * 统一口径：页面（JSON）、导出（CSV）与后台聚合共用 loadRows 同一查询，
 * 默认只统计 ACTIVE 池塘（includeDisabled=false）；池塘置为无效后三个出口同时不再出现该池数据。
 * 角色口径：FINANCE 财务看金额；PRODUCTION 生产只看数量/生物量/单据数等汇总，任何金额字段不下发。
 */
@Service
public class CostReportService {

    private final SeedlingBatchMapper batchMapper;
    private final PondMapper pondMapper;
    private final CostBalanceMapper balanceMapper;
    private final CostCarryoverMapper carryoverMapper;
    private final CostCarryoverLineMapper carryoverLineMapper;
    private final CostEntryMapper entryMapper;

    public CostReportService(SeedlingBatchMapper batchMapper,
                             PondMapper pondMapper,
                             CostBalanceMapper balanceMapper,
                             CostCarryoverMapper carryoverMapper,
                             CostCarryoverLineMapper carryoverLineMapper,
                             CostEntryMapper entryMapper) {
        this.batchMapper = batchMapper;
        this.pondMapper = pondMapper;
        this.balanceMapper = balanceMapper;
        this.carryoverMapper = carryoverMapper;
        this.carryoverLineMapper = carryoverLineMapper;
        this.entryMapper = entryMapper;
    }

    /**
     * 养殖成本分摊与批次结转报表行（后台原始口径，含金额）。
     * 页面视图与 CSV 导出都从这里取数，再按角色脱敏。
     */
    public List<Map<String, Object>> loadRows(boolean includeDisabled) {
        List<SeedlingBatch> batches = batchMapper.selectList(
                new QueryWrapper<SeedlingBatch>().orderByAsc("id"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SeedlingBatch batch : batches) {
            Pond pond = pondMapper.selectById(batch.getPondId());
            String pondStatus = pond == null ? "UNKNOWN" : pond.getStatus();
            // 同口径过滤：无效池默认在页面/导出/后台聚合中一律剔除，历史凭条与快照不删。
            if (!includeDisabled && !Pond.STATUS_ACTIVE.equals(pondStatus)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchId", batch.getId());
            row.put("batchNo", batch.getBatchNo());
            row.put("species", batch.getSpecies());
            row.put("batchStatus", batch.getStatus());
            row.put("quantity", batch.getQuantity());
            row.put("biomassKg", batch.getBiomassKg());
            row.put("pondId", batch.getPondId());
            row.put("pondCode", pond == null ? null : pond.getPondCode());
            row.put("pondName", pond == null ? null : pond.getPondName());
            row.put("pondStatus", pondStatus);

            BigDecimal grandTotal = BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
            Map<String, BigDecimal> amountByType = new LinkedHashMap<>();
            Map<String, Integer> countByType = new LinkedHashMap<>();

            if (SeedlingBatch.STATUS_HARVESTED.equals(batch.getStatus())) {
                // 已出池：金额取冻结快照，任何新单据都无法改写
                CostCarryover carryover = carryoverMapper.selectOne(
                        new QueryWrapper<CostCarryover>().eq("batch_id", batch.getId()));
                row.put("carryoverNo", carryover == null ? null : carryover.getCarryoverNo());
                row.put("carryoverTotalAmount",
                        carryover == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                                : carryover.getTotalAmount());
                List<CostCarryoverLine> lines = carryover == null ? new ArrayList<>()
                        : carryoverLineMapper.selectList(new QueryWrapper<CostCarryoverLine>()
                        .eq("carryover_id", carryover.getId()).orderByAsc("id"));
                for (String type : CostLedgerService.COST_TYPES) {
                    CostCarryoverLine line = lines.stream()
                            .filter(l -> type.equals(l.getCostType())).findFirst().orElse(null);
                    BigDecimal amount = line == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                            : line.getSettledAmount();
                    amountByType.put(type, amount);
                    countByType.put(type, line == null ? 0 : line.getEntryCount());
                    grandTotal = grandTotal.add(amount);
                }
            } else {
                List<CostBalance> balances = balanceMapper.selectList(
                        new QueryWrapper<CostBalance>().eq("batch_id", batch.getId()));
                for (String type : CostLedgerService.COST_TYPES) {
                    CostBalance balance = balances.stream()
                            .filter(b -> type.equals(b.getCostType())).findFirst().orElse(null);
                    BigDecimal amount = balance == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                            : nz(balance.getSettledAmount());
                    amountByType.put(type, amount);
                    Long count = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                            .eq("batch_id", batch.getId()).eq("cost_type", type));
                    countByType.put(type, count == null ? 0 : count.intValue());
                    grandTotal = grandTotal.add(amount);
                }
            }

            for (String type : CostLedgerService.COST_TYPES) {
                row.put(type + "Amount", amountByType.get(type));
                row.put(type + "EntryCount", countByType.get(type));
            }
            row.put("totalAmount", grandTotal);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 页面视图（JSON）：财务看金额，生产只看汇总，金额字段一律脱敏。
     */
    public Map<String, Object> pageView(String role, boolean includeDisabled) {
        boolean finance = CostLedgerService.ROLE_FINANCE.equalsIgnoreCase(role);
        List<Map<String, Object>> raw = loadRows(includeDisabled);
        List<Map<String, Object>> view = new ArrayList<>();
        BigDecimal financeGrandTotal = BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        for (Map<String, Object> row : raw) {
            Map<String, Object> masked = new LinkedHashMap<>(row);
            if (!finance) {
                for (String type : CostLedgerService.COST_TYPES) {
                    masked.remove(type + "Amount");
                }
                masked.remove("totalAmount");
                masked.remove("carryoverTotalAmount");
                masked.put("amountVisible", false);
            } else {
                masked.put("amountVisible", true);
                financeGrandTotal = financeGrandTotal.add((BigDecimal) row.get("totalAmount"));
            }
            view.add(masked);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", finance ? CostLedgerService.ROLE_FINANCE : CostLedgerService.ROLE_PRODUCTION);
        result.put("includeDisabledPonds", includeDisabled);
        result.put("caliber", "仅统计ACTIVE池塘" + (includeDisabled ? "（含无效池，本次显式放开）" : ""));
        result.put("rows", view);
        if (finance) {
            result.put("grandTotalAmount", financeGrandTotal);
        }
        return result;
    }

    /**
     * 单批次成本详情：财务看金额明细与凭证号；生产只看汇总与单据数，金额/凭证脱敏。
     */
    public Map<String, Object> batchDetail(Long batchId, String role) {
        boolean finance = CostLedgerService.ROLE_FINANCE.equalsIgnoreCase(role);
        boolean exists = batchMapper.selectCount(new QueryWrapper<SeedlingBatch>().eq("id", batchId)) > 0;
        if (!exists) {
            throw new IllegalArgumentException("投苗批次不存在，id=" + batchId);
        }
        List<Map<String, Object>> all = loadRows(true);
        Map<String, Object> row = all.stream()
                .filter(r -> batchId.equals(r.get("batchId"))).findFirst()
                .orElseGet(() -> {
                    // 兜底行（正常数据下不会走到：批次存在即能在放开无效池口径的报表中找到）
                    SeedlingBatch batch = batchMapper.selectById(batchId);
                    Pond pond = pondMapper.selectById(batch.getPondId());
                    Map<String, Object> fallback = new LinkedHashMap<>();
                    fallback.put("batchId", batch.getId());
                    fallback.put("batchNo", batch.getBatchNo());
                    fallback.put("species", batch.getSpecies());
                    fallback.put("batchStatus", batch.getStatus());
                    fallback.put("quantity", batch.getQuantity());
                    fallback.put("biomassKg", batch.getBiomassKg());
                    fallback.put("pondId", batch.getPondId());
                    fallback.put("pondCode", pond == null ? null : pond.getPondCode());
                    fallback.put("pondStatus", pond == null ? "UNKNOWN" : pond.getStatus());
                    for (String type : CostLedgerService.COST_TYPES) {
                        fallback.put(type + "Amount", BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
                        fallback.put(type + "EntryCount", 0);
                    }
                    fallback.put("totalAmount", BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
                    return fallback;
                });
        List<CostEntry> entries = entryMapper.selectList(
                new QueryWrapper<CostEntry>().eq("batch_id", batchId).orderByAsc("id"));
        List<Map<String, Object>> entryView = new ArrayList<>();
        for (CostEntry entry : entries) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("entryNo", entry.getEntryNo());
            e.put("costType", entry.getCostType());
            e.put("source", entry.getSource());
            e.put("occurDate", entry.getOccurDate());
            if (finance) {
                e.put("amount", entry.getAmount());
                e.put("voucherNo", entry.getVoucherNo());
            }
            entryView.add(e);
        }
        Map<String, Object> detail = new LinkedHashMap<>(row);
        detail.put("entries", entryView);
        if (!finance) {
            // 生产角色：行级金额与结转金额一并脱敏，只留数量/生物量/单据数汇总
            for (String type : CostLedgerService.COST_TYPES) {
                detail.remove(type + "Amount");
            }
            detail.remove("totalAmount");
            detail.remove("carryoverTotalAmount");
            detail.put("amountVisible", false);
        }
        return detail;
    }

    /**
     * CSV 导出：与页面/后台同一查询同一过滤口径；生产角色导出同样不含金额列。
     */
    public String exportCsv(String role, boolean includeDisabled) {
        boolean finance = CostLedgerService.ROLE_FINANCE.equalsIgnoreCase(role);
        List<Map<String, Object>> rows = loadRows(includeDisabled);
        StringBuilder sb = new StringBuilder("﻿");
        List<String> headers = new ArrayList<>();
        headers.add("批次号");
        headers.add("品种");
        headers.add("批次状态");
        headers.add("池塘编号");
        headers.add("池塘状态");
        headers.add("数量");
        headers.add("生物量kg");
        headers.add("饲料单据数");
        headers.add("人工单据数");
        headers.add("药品单据数");
        headers.add("能源单据数");
        if (finance) {
            headers.add("饲料金额");
            headers.add("人工金额");
            headers.add("药品金额");
            headers.add("能源金额");
            headers.add("合计金额");
            headers.add("结转单号");
        }
        sb.append(String.join(",", headers)).append("\r\n");

        for (Map<String, Object> row : rows) {
            List<String> cols = new ArrayList<>();
            cols.add(csv(row.get("batchNo")));
            cols.add(csv(row.get("species")));
            cols.add(csv(row.get("batchStatus")));
            cols.add(csv(row.get("pondCode")));
            cols.add(csv(row.get("pondStatus")));
            cols.add(csv(row.get("quantity")));
            cols.add(csv(row.get("biomassKg")));
            cols.add(csv(row.get("FEEDEntryCount")));
            cols.add(csv(row.get("LABOREntryCount")));
            cols.add(csv(row.get("MEDEntryCount")));
            cols.add(csv(row.get("ENERGYEntryCount")));
            if (finance) {
                cols.add(csv(row.get("FEEDAmount")));
                cols.add(csv(row.get("LABORAmount")));
                cols.add(csv(row.get("MEDAmount")));
                cols.add(csv(row.get("ENERGYAmount")));
                cols.add(csv(row.get("totalAmount")));
                cols.add(csv(row.get("carryoverNo")));
            }
            sb.append(String.join(",", cols)).append("\r\n");
        }
        return sb.toString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            // 财务导出金额统一两位小数，口径与页面数值一致
            return ((BigDecimal) value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP) : value;
    }
}
