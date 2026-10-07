package com.evops.aqua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.InspectionRecordMapper;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.mapper.DailyRecordMapper;
import com.evops.aqua.entity.DailyRecord;
import com.evops.aqua.med.entity.MedRequisition;
import com.evops.aqua.med.entity.MedRequisitionItem;
import com.evops.aqua.med.mapper.MedRequisitionItemMapper;
import com.evops.aqua.med.mapper.MedRequisitionMapper;
import com.evops.aqua.release.entity.ReleaseOrder;
import com.evops.aqua.release.entity.ReleaseRound;
import com.evops.aqua.release.mapper.ReleaseOrderMapper;
import com.evops.aqua.release.mapper.ReleaseRoundMapper;
import com.evops.aqua.report.entity.ReportCaliberIndicator;
import com.evops.aqua.report.entity.ReportCaliberVersion;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.entity.ReportMonthly;
import com.evops.aqua.report.entity.ReportMonthlyIndicator;
import com.evops.aqua.report.entity.ReportMonthlyInspection;
import com.evops.aqua.report.mapper.ReportMonthlyIndicatorMapper;
import com.evops.aqua.report.mapper.ReportMonthlyInspectionMapper;
import com.evops.aqua.report.mapper.ReportMonthlyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监管月报汇总与快照固化：
 * - 五个指标（投苗量/用药量/死亡率/出池量/检验结论）严格按固化口径版本取数；
 * - 缺采样数据的指标置 PENDING_FILL 并写原因，值留空——绝不以 0 代替；
 * - 每次生成都插新版本快照（月份+海区 version_seq 递增），成功即 frozen=TRUE，永不回改；
 * - 每个指标行再次固化当时口径版本号与口径 JSON，历史月报永远可还原当时口径。
 */
@Service
public class ReportSnapshotService {

    private final PondMapper pondMapper;
    private final SeedlingBatchMapper batchMapper;
    private final DailyRecordMapper dailyMapper;
    private final MedRequisitionMapper requisitionMapper;
    private final MedRequisitionItemMapper requisitionItemMapper;
    private final HarvestRecordMapper harvestMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final ReleaseRoundMapper releaseRoundMapper;
    private final ReleaseOrderMapper releaseOrderMapper;
    private final ReportMonthlyMapper monthlyMapper;
    private final ReportMonthlyIndicatorMapper indicatorMapper;
    private final ReportMonthlyInspectionMapper monthlyInspectionMapper;
    private final CaliberVersionService caliberService;
    private final ReportEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    /** 故障注入（仅验收用）：剩余强制失败次数 > 0 时，汇总开始即抛出，模拟任务失败链路。 */
    private volatile int faultRemaining;
    private volatile String faultMessage = "模拟采样汇总失败";

    public ReportSnapshotService(PondMapper pondMapper,
                                 SeedlingBatchMapper batchMapper,
                                 DailyRecordMapper dailyMapper,
                                 MedRequisitionMapper requisitionMapper,
                                 MedRequisitionItemMapper requisitionItemMapper,
                                 HarvestRecordMapper harvestMapper,
                                 InspectionRecordMapper inspectionMapper,
                                 ReleaseRoundMapper releaseRoundMapper,
                                 ReleaseOrderMapper releaseOrderMapper,
                                 ReportMonthlyMapper monthlyMapper,
                                 ReportMonthlyIndicatorMapper indicatorMapper,
                                 ReportMonthlyInspectionMapper monthlyInspectionMapper,
                                 CaliberVersionService caliberService,
                                 ReportEvidenceService evidenceService,
                                 ObjectMapper objectMapper) {
        this.pondMapper = pondMapper;
        this.batchMapper = batchMapper;
        this.dailyMapper = dailyMapper;
        this.requisitionMapper = requisitionMapper;
        this.requisitionItemMapper = requisitionItemMapper;
        this.harvestMapper = harvestMapper;
        this.inspectionMapper = inspectionMapper;
        this.releaseRoundMapper = releaseRoundMapper;
        this.releaseOrderMapper = releaseOrderMapper;
        this.monthlyMapper = monthlyMapper;
        this.indicatorMapper = indicatorMapper;
        this.monthlyInspectionMapper = monthlyInspectionMapper;
        this.caliberService = caliberService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /** 验收故障注入：接下来 times 次生成强制抛错。 */
    public void armFault(int times, String message) {
        this.faultRemaining = times;
        if (message != null) {
            this.faultMessage = message;
        }
    }

    public void disarmFault() {
        this.faultRemaining = 0;
    }

    /** 系统中出现过的全部海区（扫描任务逐海区生成）。 */
    public List<String> allSeaAreas() {
        List<Pond> ponds = pondMapper.selectList(new QueryWrapper<Pond>()
                .select("DISTINCT sea_area").isNotNull("sea_area"));
        List<String> areas = new ArrayList<>();
        for (Pond pond : ponds) {
            if (pond.getSeaArea() != null && !pond.getSeaArea().trim().isEmpty() && !areas.contains(pond.getSeaArea())) {
                areas.add(pond.getSeaArea());
            }
        }
        return areas;
    }

    /**
     * 按海区生成月报新版本快照并固化。本方法为独立事务：任务编排方在失败时整体回滚，
     * 不留半成品（失败事实由任务链路表独立事务保留）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportMonthly generateForArea(String reportMonth, String seaArea, String triggerSource,
                                         Long jobId, String operator, String requestId) {
        if (faultRemaining > 0) {
            faultRemaining--;
            throw new ReportGenerateException(faultMessage);
        }
        YearMonth ym = YearMonth.parse(reportMonth);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();

        ReportCaliberVersion caliber = caliberService.getOrCreateDefault();
        Map<String, ReportCaliberIndicator> caliberMap = new LinkedHashMap<>();
        for (ReportCaliberIndicator row : caliberService.listIndicators(caliber.getId())) {
            caliberMap.put(row.getIndicatorCode(), row);
        }

        List<Pond> ponds = pondMapper.selectList(new QueryWrapper<Pond>().eq("sea_area", seaArea));
        if (ponds.isEmpty()) {
            throw new ReportGenerateException("海区「" + seaArea + "」无在册池塘，无法生成监管月报");
        }
        List<Long> pondIds = new ArrayList<>();
        for (Pond pond : ponds) {
            pondIds.add(pond.getId());
        }

        int seq = nextSeq(reportMonth, seaArea);
        ReportMonthly report = new ReportMonthly();
        report.setReportNo(buildReportNo(reportMonth, seaArea, seq));
        report.setReportMonth(reportMonth);
        report.setSeaArea(seaArea);
        report.setVersionSeq(seq);
        report.setCaliberVersionId(caliber.getId());
        report.setCaliberVersionNo(caliber.getVersionNo());
        report.setTriggerSource(triggerSource);
        report.setStatus(ReportMonthly.STATUS_READY);
        report.setFrozen(Boolean.FALSE);
        report.setJobId(jobId);
        report.setPendingFillCount(0);
        monthlyMapper.insert(report);

        int pending = 0;
        pending += writeIndicator(report, caliberMap.get(CaliberVersionService.IND_SEEDLING),
                aggregateSeedling(pondIds, first, last));
        pending += writeIndicator(report, caliberMap.get(CaliberVersionService.IND_MEDICATION),
                aggregateMedication(pondIds, first, last));
        pending += writeIndicator(report, caliberMap.get(CaliberVersionService.IND_MORTALITY),
                aggregateMortality(pondIds, first, last));
        pending += writeIndicator(report, caliberMap.get(CaliberVersionService.IND_HARVEST),
                aggregateHarvest(pondIds, first, last));
        Aggregate inspectionAggregate = aggregateInspection(report, pondIds, first, last, ym);
        pending += writeIndicator(report, caliberMap.get(CaliberVersionService.IND_INSPECTION), inspectionAggregate);

        report.setPendingFillCount(pending);
        // 生成成功即固化：快照永久冻结，重新生成只能产生下一个 version_seq
        report.setFrozen(Boolean.TRUE);
        report.setFrozenBy(operator);
        report.setFrozenTime(LocalDateTime.now());
        monthlyMapper.updateById(report);

        evidenceService.record(ReportChangeEvidence.ENTITY_REPORT, report.getId(),
                ReportChangeEvidence.ACTION_FREEZE, requestId, operator, null,
                viewOf(report, inspectionAggregate),
                "监管月报" + report.getReportNo() + "（" + reportMonth + " / " + seaArea + "）V" + seq
                        + " 已按口径版本" + caliber.getVersionNo() + "生成并固化快照，待补指标 " + pending + " 项");
        return report;
    }

    private int nextSeq(String reportMonth, String seaArea) {
        Integer max = monthlyMapper.selectObjs(new QueryWrapper<ReportMonthly>()
                .select("COALESCE(MAX(version_seq), 0) AS m")
                .eq("report_month", reportMonth)
                .eq("sea_area", seaArea)).stream()
                .findFirst().map(o -> ((Number) o).intValue()).orElse(0);
        return max + 1;
    }

    private String buildReportNo(String month, String seaArea, int seq) {
        return "RPT-" + month.replace("-", "") + "-" + Math.abs(seaArea.hashCode()) % 100000 + "-V" + seq;
    }

    private int writeIndicator(ReportMonthly report, ReportCaliberIndicator caliber, Aggregate agg) {
        ReportMonthlyIndicator row = new ReportMonthlyIndicator();
        row.setReportId(report.getId());
        row.setIndicatorCode(caliber.getIndicatorCode());
        row.setIndicatorName(caliber.getIndicatorName());
        row.setCaliberVersionId(caliber.getVersionId());
        row.setCaliberVersionNo(report.getCaliberVersionNo());
        row.setCaliberJson(caliber.getCaliberJson());
        row.setUnit(agg.unit);
        row.setSampleCount(agg.sampleCount);
        if (agg.pending) {
            row.setIndicatorValue(null);
            row.setDataStatus(ReportMonthlyIndicator.STATUS_PENDING_FILL);
            row.setPendingReason(agg.pendingReason);
        } else {
            row.setIndicatorValue(agg.value);
            row.setDataStatus(ReportMonthlyIndicator.STATUS_NORMAL);
        }
        indicatorMapper.insert(row);
        return agg.pending ? 1 : 0;
    }

    // ---------------- 五个指标取数（严格按月、按海区池塘集合） ----------------

    private Aggregate aggregateSeedling(List<Long> pondIds, LocalDate first, LocalDate last) {
        Aggregate agg = new Aggregate();
        agg.unit = "尾";
        if (pondIds.isEmpty()) {
            return agg.markPending("报告月内该海区无投苗批次，投苗量待补（不填零）");
        }
        List<SeedlingBatch> batches = batchMapper.selectList(new QueryWrapper<SeedlingBatch>()
                .in("pond_id", pondIds)
                .ge("stock_date", first).le("stock_date", last));
        if (batches.isEmpty()) {
            return agg.markPending("报告月内该海区无投苗批次，投苗量待补（不填零）");
        }
        long sum = 0;
        for (SeedlingBatch b : batches) {
            sum += b.getQuantity() == null ? 0 : b.getQuantity();
        }
        agg.sampleCount = batches.size();
        agg.value = String.valueOf(sum);
        return agg;
    }

    private Aggregate aggregateMedication(List<Long> pondIds, LocalDate first, LocalDate last) {
        Aggregate agg = new Aggregate();
        agg.unit = "剂量(按药品计量单位合计)";
        if (pondIds.isEmpty()) {
            return agg.markPending("报告月内该海区无已审核用药领用单，用药量待补（不填零）");
        }
        List<MedRequisition> reqs = requisitionMapper.selectList(new QueryWrapper<MedRequisition>()
                .in("pond_id", pondIds)
                .eq("status", MedRequisition.STATUS_APPROVED)
                .ge("use_date", first).le("use_date", last));
        if (reqs.isEmpty()) {
            return agg.markPending("报告月内该海区无已审核用药领用单，用药量待补（不填零；草稿/待审核/驳回不计入）");
        }
        List<Long> reqIds = new ArrayList<>();
        for (MedRequisition r : reqs) {
            reqIds.add(r.getId());
        }
        List<MedRequisitionItem> items = requisitionItemMapper.selectList(new QueryWrapper<MedRequisitionItem>()
                .in("requisition_id", reqIds));
        BigDecimal sum = BigDecimal.ZERO;
        for (MedRequisitionItem item : items) {
            if (item.getEstimatedQty() != null) {
                sum = sum.add(item.getEstimatedQty());
            }
        }
        agg.sampleCount = items.size();
        agg.value = sum.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return agg;
    }

    private Aggregate aggregateMortality(List<Long> pondIds, LocalDate first, LocalDate last) {
        Aggregate agg = new Aggregate();
        agg.unit = "%";
        if (pondIds.isEmpty()) {
            return agg.markPending("报告月内该海区无死亡登记（日常记录），死亡率待补（不填零）");
        }
        List<DailyRecord> records = dailyMapper.selectList(new QueryWrapper<DailyRecord>()
                .in("pond_id", pondIds)
                .ge("record_date", first).le("record_date", last));
        if (records.isEmpty()) {
            return agg.markPending("报告月内该海区无死亡登记（日常记录），死亡率待补（不填零）");
        }
        long deaths = 0;
        List<Long> batchIds = new ArrayList<>();
        for (DailyRecord d : records) {
            deaths += d.getMortalityCount() == null ? 0 : d.getMortalityCount();
            if (d.getBatchId() != null && !batchIds.contains(d.getBatchId())) {
                batchIds.add(d.getBatchId());
            }
        }
        long stockDenominator = 0;
        if (!batchIds.isEmpty()) {
            List<SeedlingBatch> batches = batchMapper.selectList(new QueryWrapper<SeedlingBatch>()
                    .in("id", batchIds));
            for (SeedlingBatch b : batches) {
                stockDenominator += b.getQuantity() == null ? 0 : b.getQuantity();
            }
        }
        if (stockDenominator <= 0) {
            return agg.markPending("有死亡登记但涉及批次在养数量为 0，死亡率无法计算，待补（不填零）");
        }
        BigDecimal rate = BigDecimal.valueOf(deaths)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(stockDenominator), 2, RoundingMode.HALF_UP);
        agg.sampleCount = records.size();
        agg.value = rate.toPlainString();
        return agg;
    }

    private Aggregate aggregateHarvest(List<Long> pondIds, LocalDate first, LocalDate last) {
        Aggregate agg = new Aggregate();
        agg.unit = "尾";
        if (pondIds.isEmpty()) {
            return agg.markPending("报告月内该海区无出池记录，出池量待补（不填零）");
        }
        List<HarvestRecord> harvests = harvestMapper.selectList(new QueryWrapper<HarvestRecord>()
                .in("pond_id", pondIds)
                .ge("harvest_date", first).le("harvest_date", last));
        if (harvests.isEmpty()) {
            return agg.markPending("报告月内该海区无出池记录，出池量待补（不填零）");
        }
        long sum = 0;
        for (HarvestRecord h : harvests) {
            sum += h.getQuantity() == null ? 0 : h.getQuantity();
        }
        agg.sampleCount = harvests.size();
        agg.value = String.valueOf(sum);
        return agg;
    }

    private Aggregate aggregateInspection(ReportMonthly report, List<Long> pondIds,
                                          LocalDate first, LocalDate last, YearMonth ym) {
        Aggregate agg = new Aggregate();
        agg.unit = "笔";
        int pass = 0;
        int fail = 0;
        int other = 0;

        if (!pondIds.isEmpty()) {
            List<InspectionRecord> ledgerList = inspectionMapper.selectList(new QueryWrapper<InspectionRecord>()
                    .in("pond_id", pondIds)
                    .ge("inspect_date", first).le("inspect_date", last)
                    .orderByAsc("id"));
            for (InspectionRecord rec : ledgerList) {
                String cls = classifyLedgerResult(rec.getResult());
                if ("PASS".equals(cls)) {
                    pass++;
                } else if ("FAIL".equals(cls)) {
                    fail++;
                } else {
                    other++;
                }
                insertInspectionRow(report, ReportMonthlyInspection.SOURCE_LEDGER, rec.getId(),
                        rec.getPondId(), rec.getBatchId(), rec.getInspectDate(),
                        rec.getResult(), "台账抽检：样本" + rec.getSampleCount() + "，结论=" + rec.getResult());
            }

            List<ReleaseOrder> orders = releaseOrderMapper.selectList(new QueryWrapper<ReleaseOrder>()
                    .in("pond_id", pondIds));
            if (!orders.isEmpty()) {
                List<Long> orderIds = new ArrayList<>();
                Map<Long, ReleaseOrder> orderMap = new LinkedHashMap<>();
                for (ReleaseOrder o : orders) {
                    orderIds.add(o.getId());
                    orderMap.put(o.getId(), o);
                }
                LocalDateTime from = first.atStartOfDay();
                LocalDateTime to = last.atTime(23, 59, 59);
                List<ReleaseRound> rounds = releaseRoundMapper.selectList(new QueryWrapper<ReleaseRound>()
                        .in("order_id", orderIds)
                        .isNotNull("submitted_time")
                        .ge("submitted_time", from).le("submitted_time", to)
                        .orderByAsc("id"));
                for (ReleaseRound round : rounds) {
                    ReleaseOrder order = orderMap.get(round.getOrderId());
                    if ("PASS".equalsIgnoreCase(round.getConclusion())) {
                        pass++;
                    } else if ("FAIL".equalsIgnoreCase(round.getConclusion())) {
                        fail++;
                    } else {
                        other++;
                    }
                    insertInspectionRow(report, ReportMonthlyInspection.SOURCE_RELEASE_ROUND, round.getId(),
                            order == null ? null : order.getPondId(),
                            order == null ? null : order.getBatchId(),
                            round.getSubmittedTime() == null ? null : round.getSubmittedTime().toLocalDate(),
                            round.getConclusion(),
                            "放行检验第" + round.getRoundNo() + "轮：" + round.getConclusionDetail());
                }
            }
        }

        int total = pass + fail + other;
        if (total == 0) {
            return agg.markPending("报告月内该海区无台账抽检与已提交放行检验，检验结论待补（不填零）");
        }
        agg.sampleCount = total;
        agg.value = "合格" + pass + "笔/不合格" + fail + "笔/其他" + other + "笔";
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("pass", pass);
        summary.put("fail", fail);
        summary.put("other", other);
        agg.summary = summary;
        return agg;
    }

    private String classifyLedgerResult(String result) {
        if (result == null) {
            return "OTHER";
        }
        String text = result.trim();
        if (text.contains("不合格")) {
            return "FAIL";
        }
        if (text.contains("合格")) {
            return "PASS";
        }
        return "OTHER";
    }

    private void insertInspectionRow(ReportMonthly report, String source, Long sourceId,
                                     Long pondId, Long batchId, LocalDate inspectDate,
                                     String conclusion, String detail) {
        ReportMonthlyInspection row = new ReportMonthlyInspection();
        row.setReportId(report.getId());
        row.setSourceTable(source);
        row.setSourceId(sourceId);
        row.setPondId(pondId);
        row.setBatchId(batchId);
        row.setInspectDate(inspectDate);
        row.setConclusion(conclusion);
        row.setDetail(truncate(detail, 500));
        monthlyInspectionMapper.insert(row);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String viewOf(ReportMonthly report, Aggregate inspectionAggregate) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reportNo", report.getReportNo());
        view.put("reportMonth", report.getReportMonth());
        view.put("seaArea", report.getSeaArea());
        view.put("versionSeq", report.getVersionSeq());
        view.put("caliberVersionNo", report.getCaliberVersionNo());
        view.put("frozen", report.getFrozen());
        view.put("inspectionSummary", inspectionAggregate == null ? null : inspectionAggregate.summary);
        try {
            return objectMapper.writeValueAsString(view);
        } catch (Exception ex) {
            return String.valueOf(view);
        }
    }

    /** 单指标汇总中间结果。 */
    static class Aggregate {
        String value;
        String unit;
        int sampleCount;
        boolean pending;
        String pendingReason;
        Map<String, Integer> summary;

        Aggregate markPending(String reason) {
            this.pending = true;
            this.pendingReason = reason;
            return this;
        }
    }

    /** 汇总阶段业务失败（任务失败链路注入用）。 */
    public static class ReportGenerateException extends RuntimeException {
        public ReportGenerateException(String message) {
            super(message);
        }
    }
}
