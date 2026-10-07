package com.evops.aqua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.report.entity.ReportMonthly;
import com.evops.aqua.report.entity.ReportMonthlyIndicator;
import com.evops.aqua.report.entity.ReportMonthlyInspection;
import com.evops.aqua.report.mapper.ReportMonthlyIndicatorMapper;
import com.evops.aqua.report.mapper.ReportMonthlyInspectionMapper;
import com.evops.aqua.report.mapper.ReportMonthlyMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月报查询与导出：页面（JSON）与 CSV 导出共用同一 loadRows 行源与同一海区权限过滤口径，
 * 只返回当前用户授权海区的固化快照。待补指标原样导出"待补"，绝不导出为 0。
 */
@Service
public class ReportQueryService {

    private final ReportMonthlyMapper monthlyMapper;
    private final ReportMonthlyIndicatorMapper indicatorMapper;
    private final ReportMonthlyInspectionMapper inspectionMapper;
    private final ReportAccessService accessService;

    public ReportQueryService(ReportMonthlyMapper monthlyMapper,
                              ReportMonthlyIndicatorMapper indicatorMapper,
                              ReportMonthlyInspectionMapper inspectionMapper,
                              ReportAccessService accessService) {
        this.monthlyMapper = monthlyMapper;
        this.indicatorMapper = indicatorMapper;
        this.inspectionMapper = inspectionMapper;
        this.accessService = accessService;
    }

    /** 页面/导出同一行源：固化成功（frozen）的快照，按月过滤，并按登录用户海区权限过滤。 */
    public List<ReportMonthly> loadRows(String username, String reportMonth, String seaArea) {
        QueryWrapper<ReportMonthly> wrapper = new QueryWrapper<ReportMonthly>()
                .eq("frozen", Boolean.TRUE)
                .eq(reportMonth != null && !reportMonth.trim().isEmpty(), "report_month", reportMonth)
                .eq(seaArea != null && !seaArea.trim().isEmpty(), "sea_area", seaArea)
                .orderByDesc("id");
        List<ReportMonthly> rows = monthlyMapper.selectList(wrapper);
        List<ReportMonthly> filtered = new ArrayList<>();
        for (ReportMonthly row : rows) {
            if (accessService.canSeeSeaArea(username, row.getSeaArea())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /** 详情（含逐指标口径版本与检验结论），同样受海区权限约束。 */
    public Map<String, Object> detail(String username, Long reportId) {
        ReportMonthly report = monthlyMapper.selectById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("监管月报不存在：" + reportId);
        }
        accessService.requireSeaArea(username, report.getSeaArea());

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", report.getId());
        view.put("reportNo", report.getReportNo());
        view.put("reportMonth", report.getReportMonth());
        view.put("seaArea", report.getSeaArea());
        view.put("versionSeq", report.getVersionSeq());
        view.put("caliberVersionId", report.getCaliberVersionId());
        view.put("caliberVersionNo", report.getCaliberVersionNo());
        view.put("triggerSource", report.getTriggerSource());
        view.put("status", report.getStatus());
        view.put("frozen", report.getFrozen());
        view.put("frozenTime", report.getFrozenTime());
        view.put("frozenBy", report.getFrozenBy());
        view.put("pendingFillCount", report.getPendingFillCount());
        view.put("indicators", listIndicators(reportId));
        view.put("inspections", listInspections(reportId));
        view.put("versions", versions(report.getReportMonth(), report.getSeaArea()));
        return view;
    }

    public List<ReportMonthlyIndicator> listIndicators(Long reportId) {
        return indicatorMapper.selectList(new QueryWrapper<ReportMonthlyIndicator>()
                .eq("report_id", reportId).orderByAsc("id"));
    }

    public List<ReportMonthlyInspection> listInspections(Long reportId) {
        return inspectionMapper.selectList(new QueryWrapper<ReportMonthlyInspection>()
                .eq("report_id", reportId).orderByAsc("id"));
    }

    /** 同 月份+海区 的全部已固化版本（重新生成每次产生新版本，全部可查不可改）。 */
    public List<ReportMonthly> versions(String reportMonth, String seaArea) {
        return monthlyMapper.selectList(new QueryWrapper<ReportMonthly>()
                .eq("report_month", reportMonth)
                .eq("sea_area", seaArea)
                .eq("frozen", Boolean.TRUE)
                .orderByAsc("version_seq"));
    }

    /**
     * CSV 导出：与页面共用 loadRows；每个指标一列，保留该指标的口径版本号，
     * PENDING_FILL 一律写"待补"。
     */
    public String exportCsv(String username, String reportMonth, String seaArea) {
        List<ReportMonthly> rows = loadRows(username, reportMonth, seaArea);
        StringBuilder sb = new StringBuilder("﻿");
        List<String> headers = new ArrayList<>();
        headers.add("月报编号");
        headers.add("月份");
        headers.add("海区");
        headers.add("版本");
        headers.add("口径版本");
        headers.add("投苗量(尾)[口径版本]");
        headers.add("用药量[口径版本]");
        headers.add("死亡率(%)[口径版本]");
        headers.add("出池量(尾)[口径版本]");
        headers.add("检验结论[口径版本]");
        headers.add("待补指标数");
        headers.add("固化时间");
        sb.append(String.join(",", headers)).append("\r\n");

        // 同一 月份+海区 只导出最新版本（历史版本走详情页查），按月份海区升序
        Map<String, ReportMonthly> latest = new LinkedHashMap<>();
        for (ReportMonthly row : rows) {
            String key = row.getReportMonth() + "|" + row.getSeaArea();
            ReportMonthly exist = latest.get(key);
            if (exist == null || row.getVersionSeq() > exist.getVersionSeq()) {
                latest.put(key, row);
            }
        }
        List<ReportMonthly> exportRows = new ArrayList<>(latest.values());
        exportRows.sort(Comparator.comparing(ReportMonthly::getReportMonth)
                .thenComparing(ReportMonthly::getSeaArea));

        for (ReportMonthly row : exportRows) {
            Map<String, ReportMonthlyIndicator> inds = new LinkedHashMap<>();
            for (ReportMonthlyIndicator ind : listIndicators(row.getId())) {
                inds.put(ind.getIndicatorCode(), ind);
            }
            List<String> cols = new ArrayList<>();
            cols.add(csv(row.getReportNo()));
            cols.add(csv(row.getReportMonth()));
            cols.add(csv(row.getSeaArea()));
            cols.add(csv("V" + row.getVersionSeq()));
            cols.add(csv(row.getCaliberVersionNo()));
            cols.add(csv(indicatorCell(inds.get("SEEDLING"))));
            cols.add(csv(indicatorCell(inds.get("MEDICATION"))));
            cols.add(csv(indicatorCell(inds.get("MORTALITY"))));
            cols.add(csv(indicatorCell(inds.get("HARVEST"))));
            cols.add(csv(indicatorCell(inds.get("INSPECTION"))));
            cols.add(csv(row.getPendingFillCount()));
            cols.add(csv(row.getFrozenTime()));
            sb.append(String.join(",", cols)).append("\r\n");
        }
        return sb.toString();
    }

    /** 导出单元格：值 + 口径版本；待补指标写"待补(口径版本号)"，绝不写 0。 */
    private String indicatorCell(ReportMonthlyIndicator ind) {
        if (ind == null) {
            return "待补";
        }
        String caliber = "(" + ind.getCaliberVersionNo() + ")";
        if (ReportMonthlyIndicator.STATUS_PENDING_FILL.equals(ind.getDataStatus())) {
            return "待补" + caliber;
        }
        return String.valueOf(ind.getIndicatorValue()) + caliber;
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
