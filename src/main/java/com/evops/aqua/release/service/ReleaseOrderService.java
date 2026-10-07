package com.evops.aqua.release.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.release.dto.ReleaseOrderCreateRequest;
import com.evops.aqua.release.dto.ReleaseResultEnterRequest;
import com.evops.aqua.release.dto.ReleaseReturnRequest;
import com.evops.aqua.release.dto.ReleaseRoundSubmitRequest;
import com.evops.aqua.release.dto.ReleaseSignRequest;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.entity.ReleaseOrder;
import com.evops.aqua.release.entity.ReleaseReport;
import com.evops.aqua.release.entity.ReleaseResult;
import com.evops.aqua.release.entity.ReleaseRound;
import com.evops.aqua.release.entity.ReleaseRuleVersion;
import com.evops.aqua.release.entity.ReleaseSample;
import com.evops.aqua.release.entity.ReleaseSigner;
import com.evops.aqua.release.entity.ReleaseThresholdRule;
import com.evops.aqua.release.mapper.ReleaseOrderMapper;
import com.evops.aqua.release.mapper.ReleaseReportMapper;
import com.evops.aqua.release.mapper.ReleaseResultMapper;
import com.evops.aqua.release.mapper.ReleaseRoundMapper;
import com.evops.aqua.release.mapper.ReleaseSampleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出池检验单生命周期：
 * 建单（冻结「品种+用途」阈值版本，开初检轮与样品）→ 实验室录入结果（逐指标按阈值判定）
 * → 提交（必检齐全、生成放行建议，并为样品签发一一对应的检测报告）
 * → 退回补检（原轮结论/原因/时间全部保留，另开复检轮，复检绝不覆盖初检）
 * → 授权签字人电子签名（仅放行建议 PASS）→（发货服务）签名后方可发货。
 */
@Service
public class ReleaseOrderService {

    private final ReleaseOrderMapper orderMapper;
    private final ReleaseRoundMapper roundMapper;
    private final ReleaseSampleMapper sampleMapper;
    private final ReleaseReportMapper reportMapper;
    private final ReleaseResultMapper resultMapper;
    private final SeedlingBatchMapper batchMapper;
    private final ReleaseRuleVersionService ruleVersionService;
    private final ReleaseSignerService signerService;
    private final ReleaseEvidenceService evidenceService;

    public ReleaseOrderService(ReleaseOrderMapper orderMapper,
                               ReleaseRoundMapper roundMapper,
                               ReleaseSampleMapper sampleMapper,
                               ReleaseReportMapper reportMapper,
                               ReleaseResultMapper resultMapper,
                               SeedlingBatchMapper batchMapper,
                               ReleaseRuleVersionService ruleVersionService,
                               ReleaseSignerService signerService,
                               ReleaseEvidenceService evidenceService) {
        this.orderMapper = orderMapper;
        this.roundMapper = roundMapper;
        this.sampleMapper = sampleMapper;
        this.reportMapper = reportMapper;
        this.resultMapper = resultMapper;
        this.batchMapper = batchMapper;
        this.ruleVersionService = ruleVersionService;
        this.signerService = signerService;
        this.evidenceService = evidenceService;
    }

    // ------------------------------------------------------------
    // 建单
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public ReleaseOrder create(ReleaseOrderCreateRequest request) {
        SeedlingBatch batch = batchMapper.selectById(request.getBatchId());
        if (batch == null) {
            throw new IllegalArgumentException("投苗批次不存在，id=" + request.getBatchId());
        }
        // 同一批次同时只允许一张在途（未发货）检验单，避免重复放行
        Long openCount = orderMapper.selectCount(new QueryWrapper<ReleaseOrder>()
                .eq("batch_id", batch.getId())
                .ne("status", ReleaseOrder.STATUS_SHIPPED));
        if (openCount != null && openCount > 0) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已存在未发货的出池检验单，不能重复建单");
        }

        ReleaseRuleVersion version = ruleVersionService.currentActive();
        // 台账批次只有一个品种文本，作为品种编码与名称使用
        String speciesCode = batch.getSpecies() == null ? "" : batch.getSpecies().trim();
        ReleaseThresholdRule combo = ruleVersionService.resolveCombo(
                version.getId(), speciesCode, request.getUsageCode().trim());

        ReleaseOrder order = new ReleaseOrder();
        order.setBatchId(batch.getId());
        order.setPondId(batch.getPondId());
        order.setSpeciesCode(speciesCode);
        order.setSpeciesName(speciesCode);
        order.setUsageCode(request.getUsageCode().trim());
        order.setUsageName(request.getUsageName().trim());
        order.setRuleVersionId(version.getId());
        order.setStatus(ReleaseOrder.STATUS_DRAFT);
        order.setCurrentRoundNo(1);
        order.setRecommendation(ReleaseOrder.CONCLUSION_PENDING);
        // 单号在插入前确定：按批次内放行次数顺序编号（一批可多次出池放行，每次一单）
        Long batchOrderCount = orderMapper.selectCount(new QueryWrapper<ReleaseOrder>()
                .eq("batch_id", batch.getId()));
        int seq = (batchOrderCount == null ? 0 : batchOrderCount.intValue()) + 1;
        order.setOrderNo("REL-B" + batch.getId() + "-" + seq);
        orderMapper.insert(order);

        openRoundAndSample(order, 1, request.getSampleNo(), request.getSampler());

        evidenceService.record(ReleaseChangeEvidence.ENTITY_ORDER, order.getId(),
                ReleaseChangeEvidence.ACTION_CREATE, request.getRequestId(),
                ReleaseEvidenceService.currentOperator(), null, order,
                "批次" + batch.getBatchNo() + "按品种[" + speciesCode + "]用途[" + request.getUsageCode()
                        + "]创建出池检验单，冻结规则版本" + version.getVersionNo());
        return order;
    }

    // ------------------------------------------------------------
    // 实验室录入结果（当前轮）
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public ReleaseOrder enterResults(Long orderId, ReleaseResultEnterRequest request) {
        ReleaseOrder order = getOrThrow(orderId);
        requireEnterable(order);
        ReleaseRound round = currentRound(order);
        for (ReleaseResultEnterRequest.ResultInput input : request.getItems()) {
            ReleaseResult result = findResult(round.getId(), input.getIndicatorCode());
            String value = input.getValueText().trim();
            BigDecimal numeric;
            try {
                numeric = new BigDecimal(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "指标[" + result.getIndicatorName() + "]检验值不是数值：" + value);
            }
            result.setValueText(value);
            boolean qualified = judge(numeric, result.getThresholdMin(), result.getThresholdMax());
            result.setQualifiedFlag(qualified);
            result.setJudgeDetail(buildJudgeDetail(numeric, result, qualified));
            resultMapper.updateById(result);
        }
        evidenceService.record(ReleaseChangeEvidence.ENTITY_ORDER, order.getId(),
                ReleaseChangeEvidence.ACTION_ENTER, request.getRequestId(),
                ReleaseEvidenceService.currentOperator(), null, order,
                "实验室录入第" + round.getRoundNo() + "轮检验结果" + request.getItems().size() + "项");
        return order;
    }

    // ------------------------------------------------------------
    // 提交当前轮：齐全校验 + 阈值比对结论 + 生成报告与放行建议
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public ReleaseOrder submitRound(Long orderId, ReleaseRoundSubmitRequest request) {
        ReleaseOrder order = getOrThrow(orderId);
        ReleaseRound round = currentRound(order);
        // 自然幂等：当前轮已提交直接返回；同 requestId 重放也返回原单（避免跨轮误提交）
        if (ReleaseRound.STATUS_SUBMITTED.equals(round.getStatus())
                || evidenceService.exists(ReleaseChangeEvidence.ENTITY_ROUND,
                        request.getRequestId(), ReleaseChangeEvidence.ACTION_SUBMIT)) {
            return order;
        }
        ReleaseOrder before = copyOfOrder(order);
        if (!ReleaseRound.STATUS_DRAFT.equals(round.getStatus())
                && !ReleaseRound.STATUS_RETURNED.equals(round.getStatus())) {
            throw new IllegalArgumentException("当前检验轮次不是可提交状态：" + round.getStatus());
        }
        if (!ReleaseOrder.STATUS_DRAFT.equals(order.getStatus())
                && !ReleaseOrder.STATUS_RETURNED.equals(order.getStatus())) {
            throw new IllegalArgumentException("检验单当前状态不能提交：" + order.getStatus());
        }

        List<ReleaseResult> results = listResults(round.getId());
        List<String> missing = new ArrayList<>();
        boolean allQualified = true;
        int evaluated = 0;
        for (ReleaseResult r : results) {
            boolean hasValue = r.getValueText() != null && !r.getValueText().trim().isEmpty();
            if (Boolean.TRUE.equals(r.getRequiredFlag()) && !hasValue) {
                missing.add(r.getIndicatorName());
            }
            if (hasValue) {
                evaluated++;
                if (!Boolean.TRUE.equals(r.getQualifiedFlag())) {
                    allQualified = false;
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("必检指标尚未录入结果，不能提交：" + missing);
        }
        if (evaluated == 0) {
            throw new IllegalArgumentException("尚未录入任何检验结果，不能提交");
        }

        String conclusion = allQualified ? ReleaseOrder.CONCLUSION_PASS : ReleaseOrder.CONCLUSION_FAIL;
        String detail = buildConclusionDetail(round.getRoundNo(), results);

        round.setStatus(ReleaseRound.STATUS_SUBMITTED);
        round.setConclusion(conclusion);
        round.setConclusionDetail(detail);
        round.setSubmittedBy(request.getSubmittedBy().trim());
        round.setSubmittedTime(LocalDateTime.now());
        roundMapper.updateById(round);

        // 为该轮样品签发一一对应的检测报告
        issueReport(order, round, conclusion, detail, request.getSubmittedBy().trim());

        order.setStatus(ReleaseOrder.STATUS_SUBMITTED);
        order.setRecommendation(conclusion);
        order.setRecommendationDetail(detail);
        order.setSubmittedTime(LocalDateTime.now());
        orderMapper.updateById(order);

        evidenceService.record(ReleaseChangeEvidence.ENTITY_ROUND, round.getId(),
                ReleaseChangeEvidence.ACTION_SUBMIT, request.getRequestId(),
                request.getSubmittedBy().trim(), before, order,
                "第" + round.getRoundNo() + "轮提交，系统比对阈值结论=" + conclusion);
        return order;
    }

    // ------------------------------------------------------------
    // 退回补检：原轮冻结保留，另开复检轮（复检不覆盖初检）
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public ReleaseOrder returnForReinspection(Long orderId, ReleaseReturnRequest request) {
        if (evidenceService.exists(ReleaseChangeEvidence.ENTITY_ORDER,
                request.getRequestId(), ReleaseChangeEvidence.ACTION_RETURN)) {
            return getOrThrow(orderId);
        }
        ReleaseOrder before = copyOfOrder(getOrThrow(orderId));
        ReleaseOrder order = getOrThrow(orderId);
        if (!ReleaseOrder.STATUS_SUBMITTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已提交检验单才能退回补检，当前状态：" + order.getStatus());
        }
        ReleaseRound current = currentRound(order);
        if (!ReleaseRound.STATUS_SUBMITTED.equals(current.getStatus())) {
            throw new IllegalArgumentException("当前轮次未提交，不能退回：" + current.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();

        // —— 关键：冻结保留当前轮原结论、退回原因、操作时间，绝不改写其样品/报告/结果 ——
        current.setStatus(ReleaseRound.STATUS_RETURNED);
        current.setReturnReason(request.getReason().trim());
        current.setReturnOperator(request.getOperator().trim());
        current.setReturnedTime(now);
        roundMapper.updateById(current);

        order.setStatus(ReleaseOrder.STATUS_RETURNED);
        order.setReturnReason(request.getReason().trim());
        order.setReturnOperator(request.getOperator().trim());
        order.setReturnedTime(now);
        int nextRoundNo = order.getCurrentRoundNo() + 1;
        order.setCurrentRoundNo(nextRoundNo);
        orderMapper.updateById(order);

        // 另开复检轮与新样品，结果行按同一冻结版本重建；历史轮原数据原样保留
        openRoundAndSample(order, nextRoundNo, null, request.getOperator().trim());

        evidenceService.record(ReleaseChangeEvidence.ENTITY_ORDER, order.getId(),
                ReleaseChangeEvidence.ACTION_RETURN, request.getRequestId(),
                request.getOperator().trim(), before, order,
                "退回补检：保留第" + current.getRoundNo() + "轮原结论[" + current.getConclusion()
                        + "]与原因，新开第" + nextRoundNo + "复检轮（复检不覆盖初检）。原因：" + request.getReason());
        return order;
    }

    // ------------------------------------------------------------
    // 授权签字人电子签名：仅放行建议 PASS 才可签，签名后才能发货
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public ReleaseOrder sign(Long orderId, ReleaseSignRequest request) {
        if (evidenceService.exists(ReleaseChangeEvidence.ENTITY_ORDER,
                request.getRequestId(), ReleaseChangeEvidence.ACTION_SIGN)) {
            return getOrThrow(orderId);
        }
        ReleaseOrder before = copyOfOrder(getOrThrow(orderId));
        ReleaseOrder order = getOrThrow(orderId);
        if (ReleaseOrder.STATUS_SIGNED.equals(order.getStatus())
                || ReleaseOrder.STATUS_SHIPPED.equals(order.getStatus())) {
            return order; // 重复/发货后签名请求幂等返回
        }
        if (!ReleaseOrder.STATUS_SUBMITTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已提交检验单才能电子签名，当前状态：" + order.getStatus());
        }
        ReleaseRound round = currentRound(order);
        if (!ReleaseRound.STATUS_SUBMITTED.equals(round.getStatus())) {
            throw new IllegalArgumentException("当前检验轮次未提交，不能签名：" + round.getStatus());
        }
        if (!ReleaseOrder.CONCLUSION_PASS.equals(round.getConclusion())) {
            throw new IllegalArgumentException("当前轮系统比对结论为" + round.getConclusion()
                    + "，不予放行签名；请退回补检合格后再签");
        }
        ReleaseSigner signer = signerService.authenticate(request.getEmployeeNo().trim(), request.getSignSecret());

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(ReleaseOrder.STATUS_SIGNED);
        order.setSignerEmployeeNo(signer.getEmployeeNo());
        order.setSignerName(signer.getSignerName());
        order.setSignatureNo("SIG-" + order.getId());
        order.setSignedTime(now);
        order.setSignatureRemark(request.getRemark());
        orderMapper.updateById(order);

        evidenceService.record(ReleaseChangeEvidence.ENTITY_ORDER, order.getId(),
                ReleaseChangeEvidence.ACTION_SIGN, request.getRequestId(),
                signer.getEmployeeNo(), before, order,
                "授权签字人" + signer.getSignerName() + "(" + signer.getEmployeeNo()
                        + ")电子签名放行，签名号SIG-" + order.getId() + "，批次可进入发货");
        return order;
    }

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    public ReleaseOrder getOrThrow(Long id) {
        ReleaseOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("出池检验单不存在，id=" + id);
        }
        return order;
    }

    public List<ReleaseOrder> listOrders(Long batchId, Long pondId, String status) {
        QueryWrapper<ReleaseOrder> w = new QueryWrapper<>();
        w.eq(batchId != null, "batch_id", batchId);
        w.eq(pondId != null, "pond_id", pondId);
        w.eq(status != null && !status.trim().isEmpty(), "status", status);
        w.orderByAsc("id");
        return orderMapper.selectList(w);
    }

    /** 检验单详情：单头 + 各轮（样品、一一对应报告、结果行），便于核对初检/复检与样品报告对应。 */
    public Map<String, Object> detail(Long orderId) {
        ReleaseOrder order = getOrThrow(orderId);
        List<ReleaseRound> rounds = roundMapper.selectList(new QueryWrapper<ReleaseRound>()
                .eq("order_id", orderId).orderByAsc("round_no"));
        List<Map<String, Object>> roundPayload = new ArrayList<>();
        for (ReleaseRound round : rounds) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("round", round);
            List<ReleaseSample> samples = sampleMapper.selectList(new QueryWrapper<ReleaseSample>()
                    .eq("round_id", round.getId()));
            ReleaseSample sample = samples.isEmpty() ? null : samples.get(0);
            rm.put("sample", sample);
            ReleaseReport report = sample == null ? null : reportMapper.selectOne(
                    new QueryWrapper<ReleaseReport>().eq("sample_id", sample.getId()));
            rm.put("report", report);
            rm.put("results", listResults(round.getId()));
            roundPayload.add(rm);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("rounds", roundPayload);
        return detail;
    }

    /** 样品↔报告对应核对：应返回每个样品恰好一份报告（1:1）。 */
    public Map<String, Object> sampleReportCorrespondence(Long orderId) {
        getOrThrow(orderId);
        List<ReleaseSample> samples = sampleMapper.selectList(new QueryWrapper<ReleaseSample>()
                .eq("order_id", orderId).orderByAsc("id"));
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean oneToOne = true;
        for (ReleaseSample s : samples) {
            List<ReleaseReport> reports = reportMapper.selectList(new QueryWrapper<ReleaseReport>()
                    .eq("sample_id", s.getId()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sampleNo", s.getSampleNo());
            row.put("reportCount", reports.size());
            row.put("reportNo", reports.isEmpty() ? null : reports.get(0).getReportNo());
            if (reports.size() != 1) {
                oneToOne = false;
            }
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleCount", samples.size());
        result.put("oneToOne", oneToOne);
        result.put("rows", rows);
        return result;
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private void openRoundAndSample(ReleaseOrder order, int roundNo, String round1SampleNo, String sampler) {
        ReleaseRound round = new ReleaseRound();
        round.setOrderId(order.getId());
        round.setRoundNo(roundNo);
        round.setRuleVersionId(order.getRuleVersionId());
        round.setStatus(ReleaseRound.STATUS_DRAFT);
        roundMapper.insert(round);

        String baseSampleNo = round1SampleNo == null || round1SampleNo.trim().isEmpty()
                ? "SMP-" + order.getId() : round1SampleNo.trim();
        String sampleNo = roundNo == 1 ? baseSampleNo : baseSampleNo + "-R" + roundNo;
        Long dupSample = sampleMapper.selectCount(new QueryWrapper<ReleaseSample>()
                .eq("sample_no", sampleNo));
        if (dupSample != null && dupSample > 0) {
            throw new IllegalArgumentException("样品编号已存在：" + sampleNo);
        }
        ReleaseSample sample = new ReleaseSample();
        sample.setOrderId(order.getId());
        sample.setRoundId(round.getId());
        sample.setSampleNo(sampleNo);
        sample.setSampler(sampler);
        sample.setCollectedTime(LocalDateTime.now());
        sampleMapper.insert(sample);

        // 按「品种 + 用途」冻结本轮阈值快照为结果行
        List<ReleaseThresholdRule> rules = ruleVersionService.thresholdsOf(
                order.getRuleVersionId(), order.getSpeciesCode(), order.getUsageCode());
        for (ReleaseThresholdRule rule : rules) {
            ReleaseResult r = new ReleaseResult();
            r.setRoundId(round.getId());
            r.setIndicatorCode(rule.getIndicatorCode());
            r.setIndicatorName(rule.getIndicatorName());
            r.setUnit(rule.getUnit());
            r.setThresholdMin(rule.getMinValue());
            r.setThresholdMax(rule.getMaxValue());
            r.setRequiredFlag(rule.getRequiredFlag());
            resultMapper.insert(r);
        }

        evidenceService.record(ReleaseChangeEvidence.ENTITY_ROUND, round.getId(),
                ReleaseChangeEvidence.ACTION_CREATE, null,
                ReleaseEvidenceService.currentOperator(), null, round,
                "创建第" + roundNo + "检验轮与样品" + sampleNo + "，冻结阈值指标" + rules.size() + "项");
    }

    private void issueReport(ReleaseOrder order, ReleaseRound round, String conclusion,
                             String detail, String issuedBy) {
        ReleaseSample sample = sampleMapper.selectOne(new QueryWrapper<ReleaseSample>()
                .eq("round_id", round.getId()));
        Long exists = reportMapper.selectCount(new QueryWrapper<ReleaseReport>()
                .eq("sample_id", sample.getId()));
        if (exists != null && exists > 0) {
            return; // 报告与样品 1:1，重复提交不多发
        }
        ReleaseReport report = new ReleaseReport();
        report.setSampleId(sample.getId());
        report.setOrderId(order.getId());
        report.setRoundId(round.getId());
        report.setReportNo("RPT-" + sample.getId());
        report.setConclusion(conclusion);
        report.setConclusionDetail(detail);
        report.setIssuedBy(issuedBy);
        report.setIssuedTime(LocalDateTime.now());
        reportMapper.insert(report);
        evidenceService.record(ReleaseChangeEvidence.ENTITY_REPORT, report.getId(),
                ReleaseChangeEvidence.ACTION_CREATE, null, issuedBy, null, report,
                "样品" + sample.getSampleNo() + "签发检测报告" + report.getReportNo()
                        + "（样品↔报告一一对应），结论=" + conclusion);
    }

    private boolean judge(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        return max == null || value.compareTo(max) <= 0;
    }

    private String buildJudgeDetail(BigDecimal value, ReleaseResult r, boolean qualified) {
        StringBuilder sb = new StringBuilder();
        sb.append("实测").append(value.toPlainString());
        if (r.getUnit() != null && !r.getUnit().isEmpty()) {
            sb.append(r.getUnit());
        }
        sb.append("，阈值闭区间[");
        sb.append(r.getThresholdMin() == null ? "−∞" : r.getThresholdMin().toPlainString());
        sb.append(", ");
        sb.append(r.getThresholdMax() == null ? "+∞" : r.getThresholdMax().toPlainString());
        sb.append("]，判定").append(qualified ? "合格" : "不合格");
        return sb.toString();
    }

    private String buildConclusionDetail(int roundNo, List<ReleaseResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("第").append(roundNo).append("轮系统阈值比对：");
        int pass = 0;
        int fail = 0;
        List<String> failNames = new ArrayList<>();
        for (ReleaseResult r : results) {
            if (r.getValueText() == null || r.getValueText().trim().isEmpty()) {
                continue;
            }
            if (Boolean.TRUE.equals(r.getQualifiedFlag())) {
                pass++;
            } else {
                fail++;
                failNames.add(r.getIndicatorName() + "(" + r.getValueText() + ")");
            }
        }
        sb.append("合格").append(pass).append("项");
        if (fail > 0) {
            sb.append("，不合格").append(fail).append("项：").append(String.join("、", failNames));
        }
        sb.append("；放行建议=").append(fail == 0 ? "PASS" : "FAIL");
        return sb.toString();
    }

    private void requireEnterable(ReleaseOrder order) {
        if (!ReleaseOrder.STATUS_DRAFT.equals(order.getStatus())
                && !ReleaseOrder.STATUS_RETURNED.equals(order.getStatus())) {
            throw new IllegalArgumentException("检验单已提交/签名/发货，不能再录入结果，状态=" + order.getStatus());
        }
    }

    private ReleaseRound currentRound(ReleaseOrder order) {
        List<ReleaseRound> rounds = roundMapper.selectList(new QueryWrapper<ReleaseRound>()
                .eq("order_id", order.getId())
                .eq("round_no", order.getCurrentRoundNo()));
        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("检验单当前轮次缺失：roundNo=" + order.getCurrentRoundNo());
        }
        return rounds.get(0);
    }

    private ReleaseResult findResult(Long roundId, String indicatorCode) {
        List<ReleaseResult> rows = resultMapper.selectList(new QueryWrapper<ReleaseResult>()
                .eq("round_id", roundId).eq("indicator_code", indicatorCode));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("当前检验轮次无该指标：" + indicatorCode);
        }
        return rows.get(0);
    }

    private List<ReleaseResult> listResults(Long roundId) {
        return resultMapper.selectList(new QueryWrapper<ReleaseResult>()
                .eq("round_id", roundId).orderByAsc("id"));
    }

    private ReleaseOrder copyOfOrder(ReleaseOrder s) {
        ReleaseOrder c = new ReleaseOrder();
        c.setId(s.getId());
        c.setOrderNo(s.getOrderNo());
        c.setBatchId(s.getBatchId());
        c.setPondId(s.getPondId());
        c.setSpeciesCode(s.getSpeciesCode());
        c.setSpeciesName(s.getSpeciesName());
        c.setUsageCode(s.getUsageCode());
        c.setUsageName(s.getUsageName());
        c.setRuleVersionId(s.getRuleVersionId());
        c.setStatus(s.getStatus());
        c.setCurrentRoundNo(s.getCurrentRoundNo());
        c.setRecommendation(s.getRecommendation());
        c.setRecommendationDetail(s.getRecommendationDetail());
        c.setSubmittedTime(s.getSubmittedTime());
        c.setReturnedTime(s.getReturnedTime());
        c.setReturnReason(s.getReturnReason());
        c.setReturnOperator(s.getReturnOperator());
        c.setSignerEmployeeNo(s.getSignerEmployeeNo());
        c.setSignerName(s.getSignerName());
        c.setSignatureNo(s.getSignatureNo());
        c.setSignedTime(s.getSignedTime());
        c.setShipmentId(s.getShipmentId());
        return c;
    }
}
