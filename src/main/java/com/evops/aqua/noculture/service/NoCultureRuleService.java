package com.evops.aqua.noculture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.noculture.dto.RuleCreateRequest;
import com.evops.aqua.noculture.dto.RuleRevokeRequest;
import com.evops.aqua.noculture.entity.NoCultureChangeEvidence;
import com.evops.aqua.noculture.entity.NoCultureCompensation;
import com.evops.aqua.noculture.entity.NoCultureFreeze;
import com.evops.aqua.noculture.entity.NoCultureMatch;
import com.evops.aqua.noculture.entity.NoCultureRule;
import com.evops.aqua.noculture.mapper.NoCultureCompensationMapper;
import com.evops.aqua.noculture.mapper.NoCultureFreezeMapper;
import com.evops.aqua.noculture.mapper.NoCultureMatchMapper;
import com.evops.aqua.noculture.mapper.NoCultureRuleMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海区禁养规则维护与批次冻结/补偿。
 * 登记（requestId 幂等）：校验范围/时间/依据文件后落规则，随即扫描全部在途批次——
 *   空间命中的在途（STOCKED）批次逐批冻结，匹配记录与冻结账双向外键对应；
 *   空间命中但已完成（HARVESTED）的批次只写 COMPLETED_NOTICE 提示，绝不回写台账；
 *   单批冻结失败不影响其他批次（部分成功）：留 FAILED 匹配与独立事务失败证据，可补偿勾稽。
 * 撤销（requestId 幂等）：规则置 REVOKED，逐冻结账补偿——
 *   批次仍被其他生效重叠规则冻结的记 SKIPPED_STILL_BOUND（保持冻结），其余 RELEASED 解冻。
 */
@Service
public class NoCultureRuleService {

    private final NoCultureRuleMapper ruleMapper;
    private final NoCultureMatchMapper matchMapper;
    private final NoCultureFreezeMapper freezeMapper;
    private final NoCultureCompensationMapper compensationMapper;
    private final SeedlingBatchMapper batchMapper;
    private final PondMapper pondMapper;
    private final NoCultureSpatialMatcher spatialMatcher;
    private final NoCultureEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public NoCultureRuleService(NoCultureRuleMapper ruleMapper,
                                NoCultureMatchMapper matchMapper,
                                NoCultureFreezeMapper freezeMapper,
                                NoCultureCompensationMapper compensationMapper,
                                SeedlingBatchMapper batchMapper,
                                PondMapper pondMapper,
                                NoCultureSpatialMatcher spatialMatcher,
                                NoCultureEvidenceService evidenceService,
                                ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.matchMapper = matchMapper;
        this.freezeMapper = freezeMapper;
        this.compensationMapper = compensationMapper;
        this.batchMapper = batchMapper;
        this.pondMapper = pondMapper;
        this.spatialMatcher = spatialMatcher;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 登记禁养规则并扫描冻结在途批次。整单一个事务：规则与全部匹配/冻结账同成败，
     * 单批异常被捕获降级为 FAILED 匹配（独立证据），不影响其余批次——部分成功也账账对应。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(RuleCreateRequest request) {
        NoCultureRule existing = ruleMapper.selectOne(
                new QueryWrapper<NoCultureRule>().eq("request_id", request.getRequestId()));
        if (existing != null) {
            return ruleView(existing, scanSummary(existing.getId()), true);
        }
        NoCultureRule duplicatedNo = ruleMapper.selectOne(
                new QueryWrapper<NoCultureRule>().eq("rule_no", request.getRuleNo()));
        if (duplicatedNo != null) {
            throw new IllegalArgumentException("禁养规则编号已存在：" + request.getRuleNo());
        }

        String scope = normalizeScope(request.getScopeType());
        String polygonJson = null;
        if (NoCultureRule.SCOPE_POLYGON.equals(scope)) {
            polygonJson = validatePolygon(request.getPolygon());
            if (request.getSeaArea() == null || request.getSeaArea().trim().isEmpty()) {
                throw new IllegalArgumentException("多边形禁养规则仍须登记所属海区用于归口");
            }
        } else {
            if (request.getSeaArea() == null || request.getSeaArea().trim().isEmpty()) {
                throw new IllegalArgumentException("整海区禁养规则必须填写所属海区");
            }
        }
        if (request.getEffectiveTo() != null
                && !request.getEffectiveTo().isAfter(request.getEffectiveFrom())) {
            throw new IllegalArgumentException("生效结束日必须晚于生效开始日（时间按半开区间[from,to)判定）");
        }

        NoCultureRule rule = new NoCultureRule();
        rule.setRuleNo(request.getRuleNo());
        rule.setSeaArea(request.getSeaArea());
        rule.setScopeType(scope);
        rule.setPolygonJson(polygonJson);
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setBasisDocNo(request.getBasisDocNo());
        rule.setBasisDocName(request.getBasisDocName());
        rule.setStrictLevel(request.getStrictLevel() == null ? 1 : request.getStrictLevel());
        rule.setStatus(NoCultureRule.STATUS_ACTIVE);
        rule.setPublishedTime(LocalDateTime.now());
        rule.setOperator(request.getOperator());
        rule.setRequestId(request.getRequestId());
        rule.setRemark(request.getRemark());
        ruleMapper.insert(rule);
        evidenceService.record(NoCultureChangeEvidence.ENTITY_RULE, rule.getId(),
                NoCultureChangeEvidence.ACTION_CREATE_RULE, request.getRequestId(),
                request.getOperator(), null, rule,
                "登记禁养规则" + rule.getRuleNo() + "：范围类型" + scope + "，海区"
                        + rule.getSeaArea() + "，生效[" + rule.getEffectiveFrom() + ","
                        + (rule.getEffectiveTo() == null ? "长期" : rule.getEffectiveTo())
                        + ")，严格级别" + rule.getStrictLevel() + "，依据《"
                        + rule.getBasisDocName() + "》" + rule.getBasisDocNo());

        ScanSummary result = scanAndFreeze(rule, request.getOperator(), request.getRequestId());
        return ruleView(rule, result, false);
    }

    /**
     * 撤销规则并逐冻结账补偿解冻。重复撤销幂等：已撤销则直接回放既有补偿汇总，不多写。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> revoke(Long ruleId, RuleRevokeRequest request) {
        NoCultureRule rule = getOrThrow(ruleId);
        if (NoCultureRule.STATUS_REVOKED.equals(rule.getStatus())) {
            return revokeView(rule, compensationSummary(ruleId), true);
        }
        rule.setStatus(NoCultureRule.STATUS_REVOKED);
        rule.setRevokedBy(request.getOperator());
        rule.setRevokedTime(LocalDateTime.now());
        rule.setRevokeReason(request.getReason());
        ruleMapper.updateById(rule);
        evidenceService.record(NoCultureChangeEvidence.ENTITY_RULE, rule.getId(),
                NoCultureChangeEvidence.ACTION_REVOKE_RULE, request.getRequestId(),
                request.getOperator(), NoCultureRule.STATUS_ACTIVE, rule,
                "撤销禁养规则" + rule.getRuleNo() + "，原因：" + request.getReason()
                        + "；逐冻结账补偿，仍被他规冻结的保持冻结");

        int released = 0;
        int skipped = 0;
        List<NoCultureFreeze> freezes = freezeMapper.selectList(new QueryWrapper<NoCultureFreeze>()
                .eq("rule_id", ruleId)
                .eq("status", NoCultureFreeze.STATUS_FROZEN)
                .orderByAsc("id"));
        for (NoCultureFreeze freeze : freezes) {
            boolean stillBound = !otherActiveFreezes(freeze.getBatchId(), ruleId).isEmpty();
            NoCultureCompensation compensation = new NoCultureCompensation();
            compensation.setRuleId(ruleId);
            compensation.setFreezeId(freeze.getId());
            compensation.setBatchId(freeze.getBatchId());
            compensation.setOperator(request.getOperator());
            compensation.setRequestId(request.getRequestId() + "#F" + freeze.getId());

            // 本规则撤销：其自身冻结账一律 RELEASED；是否真正放行取决于是否仍被他规冻结。
            freeze.setStatus(NoCultureFreeze.STATUS_RELEASED);
            freeze.setUnfrozenBy(request.getOperator());
            freeze.setUnfrozenTime(LocalDateTime.now());
            freezeMapper.updateById(freeze);

            if (stillBound) {
                skipped++;
                compensation.setResult(NoCultureCompensation.RESULT_SKIPPED_STILL_BOUND);
                compensation.setDetail("批次id=" + freeze.getBatchId() + "仍被其他生效重叠禁养规则冻结（规则id："
                        + joinRuleIds(otherActiveFreezes(freeze.getBatchId(), ruleId)) + "），本次补偿不解冻");
                compensationMapper.insert(compensation);
                evidenceService.record(NoCultureChangeEvidence.ENTITY_COMPENSATION, compensation.getId(),
                        NoCultureChangeEvidence.ACTION_SKIP_UNFREEZE, compensation.getRequestId(),
                        request.getOperator(), freeze, compensation, compensation.getDetail());
            } else {
                released++;
                compensation.setResult(NoCultureCompensation.RESULT_RELEASED);
                compensation.setDetail("批次id=" + freeze.getBatchId() + "无其他生效禁养规则约束，补偿解冻放行");
                compensationMapper.insert(compensation);
                evidenceService.record(NoCultureChangeEvidence.ENTITY_FREEZE, freeze.getId(),
                        NoCultureChangeEvidence.ACTION_UNFREEZE, compensation.getRequestId(),
                        request.getOperator(), NoCultureFreeze.STATUS_FROZEN, freeze,
                        "撤销规则" + rule.getRuleNo() + "补偿解冻批次id=" + freeze.getBatchId());
            }
        }

        ScanSummary summary = new ScanSummary();
        summary.released = released;
        summary.skippedStillBound = skipped;
        return revokeView(rule, summary, false);
    }

    public NoCultureRule getOrThrow(Long id) {
        NoCultureRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new IllegalArgumentException("禁养规则不存在，id=" + id);
        }
        return rule;
    }

    public List<NoCultureRule> listRules(String status) {
        QueryWrapper<NoCultureRule> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return ruleMapper.selectList(wrapper);
    }

    public List<NoCultureMatch> listMatches(Long ruleId, Long batchId) {
        QueryWrapper<NoCultureMatch> wrapper = new QueryWrapper<>();
        wrapper.eq(ruleId != null, "rule_id", ruleId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByAsc("id");
        return matchMapper.selectList(wrapper);
    }

    public List<NoCultureFreeze> listFreezes(Long ruleId, Long batchId, String status) {
        QueryWrapper<NoCultureFreeze> wrapper = new QueryWrapper<>();
        wrapper.eq(ruleId != null, "rule_id", ruleId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return freezeMapper.selectList(wrapper);
    }

    public List<NoCultureCompensation> listCompensations(Long ruleId, Long batchId) {
        QueryWrapper<NoCultureCompensation> wrapper = new QueryWrapper<>();
        wrapper.eq(ruleId != null, "rule_id", ruleId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByAsc("id");
        return compensationMapper.selectList(wrapper);
    }

    /**
     * 勾稽核对：禁养区域（规则）→ 空间匹配 → 冻结账 → 撤销补偿逐条对应，无悬挂。
     * 返回每条匹配链路与 orphan 计数；出现孤儿即 balanced=false（部分成功也必须对应）。
     */
    public Map<String, Object> reconcile(Long ruleId) {
        List<NoCultureMatch> matches = listMatches(ruleId, null);
        List<Map<String, Object>> links = new ArrayList<>();
        int orphanMatch = 0;
        int orphanFreeze = 0;
        for (NoCultureMatch match : matches) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("matchId", match.getId());
            link.put("batchId", match.getBatchId());
            link.put("result", match.getResult());
            NoCultureFreeze freeze = match.getFreezeId() == null ? null
                    : freezeMapper.selectById(match.getFreezeId());
            link.put("freezeId", freeze == null ? null : freeze.getId());
            link.put("freezeStatus", freeze == null ? null : freeze.getStatus());
            NoCultureCompensation compensation = freeze == null ? null
                    : compensationMapper.selectOne(new QueryWrapper<NoCultureCompensation>()
                            .eq("freeze_id", freeze.getId()));
            link.put("compensationId", compensation == null ? null : compensation.getId());
            link.put("compensationResult", compensation == null ? null : compensation.getResult());

            if (NoCultureMatch.RESULT_FROZEN.equals(match.getResult())) {
                if (freeze == null) {
                    orphanMatch++;
                } else {
                    // 已撤销规则的每条冻结都必须有补偿对应（RELEASED 或 SKIPPED_STILL_BOUND）
                    NoCultureRule rule = ruleMapper.selectById(match.getRuleId());
                    if (NoCultureRule.STATUS_REVOKED.equals(rule.getStatus()) && compensation == null) {
                        orphanFreeze++;
                    }
                }
            }
            links.add(link);
        }
        // 反向：不得存在无匹配的冻结账
        Long freezesWithoutMatch = freezeMapper.selectCount(new QueryWrapper<NoCultureFreeze>()
                .eq(ruleId != null, "rule_id", ruleId)
                .isNull("match_id"));
        boolean balanced = orphanMatch == 0 && orphanFreeze == 0
                && (freezesWithoutMatch == null || freezesWithoutMatch == 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("balanced", balanced);
        result.put("links", links);
        result.put("orphanMatchCount", orphanMatch);
        result.put("orphanFreezeCount", orphanFreeze);
        result.put("freezeWithoutMatchCount", freezesWithoutMatch);
        return result;
    }

    // ------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------

    /**
     * 扫描在途批次生成冻结清单（发布事务内）：
     *   STOCKED 在途批显式按状态查询，空间命中即冻结；
     *   HARVESTED 已完成批单独查询，命中只写 COMPLETED_NOTICE，绝不回写台账；
     *   其余状态一律不进冻结清单（不能把未知状态当在途误冻）。
     * 空间判定本身抛错时不再静默吞成"不命中"：留 SPATIAL_FAILED 匹配，
     * 并以独立事务写失败证据（原始凭证），即便发布事务回滚凭证也在。
     * 单批冻结异常隔离为 FAILED 匹配 + 独立事务证据，不中断整单——部分成功。
     */
    private ScanSummary scanAndFreeze(NoCultureRule rule, String operator, String requestId) {
        ScanSummary result = new ScanSummary();
        LocalDate publishDate = rule.getPublishedTime() == null
                ? LocalDate.now() : rule.getPublishedTime().toLocalDate();
        boolean windowStillOpen = rule.getEffectiveTo() == null
                || rule.getEffectiveTo().isAfter(publishDate);
        if (!windowStillOpen) {
            result.detail = "规则生效区间在发布日" + publishDate + "已全部过去，不冻结当前批次";
            return result;
        }

        // 批次查询条件显式化：在途、已完成分别取，冻结清单只允许由 STOCKED 命中构成。
        List<SeedlingBatch> stocked = batchMapper.selectList(new QueryWrapper<SeedlingBatch>()
                .eq("status", SeedlingBatch.STATUS_STOCKED).orderByAsc("id"));
        List<SeedlingBatch> harvested = batchMapper.selectList(new QueryWrapper<SeedlingBatch>()
                .eq("status", SeedlingBatch.STATUS_HARVESTED).orderByAsc("id"));

        for (SeedlingBatch batch : stocked) {
            scanOne(rule, batch, operator, requestId, result, false);
        }
        for (SeedlingBatch batch : harvested) {
            scanOne(rule, batch, operator, requestId, result, true);
        }
        return result;
    }

    private void scanOne(NoCultureRule rule, SeedlingBatch batch, String operator,
                         String requestId, ScanSummary result, boolean harvested) {
        Pond pond = pondMapper.selectById(batch.getPondId());
        boolean hit;
        try {
            hit = spatialMatcher.hits(rule, pond);
        } catch (Exception ex) {
            // 空间判定异常绝不允许静默漏冻：失败匹配 + 独立事务原始凭证，等待人工/补偿核实。
            insertSpatialFailure(rule, batch, pond, operator, requestId, ex, result);
            return;
        }
        if (!hit) {
            return;
        }
        try {
            if (harvested) {
                insertCompletedNotice(rule, batch, pond, operator, requestId, result);
            } else {
                insertFreeze(rule, batch, pond, operator, requestId, result);
            }
        } catch (Exception ex) {
            insertFailedMatch(rule, batch, pond, operator, requestId, ex, result);
        }
    }

    private void insertFreeze(NoCultureRule rule, SeedlingBatch batch, Pond pond,
                              String operator, String requestId, ScanSummary result) {
        // 同规则同批已冻结（历史补扫）不重复写，账不重复。
        NoCultureFreeze exist = freezeMapper.selectOne(new QueryWrapper<NoCultureFreeze>()
                .eq("rule_id", rule.getId()).eq("batch_id", batch.getId()));
        if (exist != null) {
            result.existed++;
            return;
        }
        String hitDetail = buildHitDetail(rule, pond);

        NoCultureFreeze freeze = new NoCultureFreeze();
        freeze.setRuleId(rule.getId());
        freeze.setBatchId(batch.getId());
        freeze.setPondId(pond.getId());
        freeze.setStatus(NoCultureFreeze.STATUS_FROZEN);
        freeze.setStrictLevel(rule.getStrictLevel());
        freeze.setHitDetail(hitDetail);
        freeze.setFrozenBy(operator);
        freeze.setFrozenTime(LocalDateTime.now());
        freeze.setRequestId(requestId + "#B" + batch.getId());
        freezeMapper.insert(freeze);

        NoCultureMatch match = new NoCultureMatch();
        match.setRuleId(rule.getId());
        match.setBatchId(batch.getId());
        match.setPondId(pond.getId());
        match.setResult(NoCultureMatch.RESULT_FROZEN);
        match.setFreezeId(freeze.getId());
        match.setSpatialHit(true);
        match.setDetail(hitDetail);
        match.setRequestId(requestId + "#M" + batch.getId());
        matchMapper.insert(match);

        // 双向外键对应：冻结账回指匹配记录。
        freeze.setMatchId(match.getId());
        freezeMapper.updateById(freeze);

        evidenceService.record(NoCultureChangeEvidence.ENTITY_FREEZE, freeze.getId(),
                NoCultureChangeEvidence.ACTION_FREEZE, freeze.getRequestId(),
                operator, null, freeze,
                "规则" + rule.getRuleNo() + "新增，空间命中在途批次" + batch.getBatchNo()
                        + "（池id=" + pond.getId() + "），冻结其投苗/转池/捕捞；" + hitDetail);
        result.frozen++;
    }

    private void insertCompletedNotice(NoCultureRule rule, SeedlingBatch batch, Pond pond,
                                       String operator, String requestId, ScanSummary result) {
        NoCultureMatch exist = matchMapper.selectOne(new QueryWrapper<NoCultureMatch>()
                .eq("rule_id", rule.getId()).eq("batch_id", batch.getId()));
        if (exist != null) {
            result.existed++;
            return;
        }
        String detail = "规则" + rule.getRuleNo() + "空间命中批次" + batch.getBatchNo()
                + "，但该批次已完成（HARVESTED），仅提示，不回写台账";
        NoCultureMatch match = new NoCultureMatch();
        match.setRuleId(rule.getId());
        match.setBatchId(batch.getId());
        match.setPondId(pond.getId());
        match.setResult(NoCultureMatch.RESULT_COMPLETED_NOTICE);
        match.setSpatialHit(true);
        match.setDetail(detail);
        match.setRequestId(requestId + "#M" + batch.getId());
        matchMapper.insert(match);
        evidenceService.record(NoCultureChangeEvidence.ENTITY_MATCH, match.getId(),
                NoCultureChangeEvidence.ACTION_COMPLETED_NOTICE, match.getRequestId(),
                operator, null, match, detail);
        result.completedNotice++;
    }

    /** 部分成功：单批冻结失败留 FAILED 匹配与独立事务证据，不随主事务回滚。 */
    private void insertFailedMatch(NoCultureRule rule, SeedlingBatch batch, Pond pond,
                                   String operator, String requestId, Exception ex,
                                   ScanSummary result) {
        try {
            NoCultureMatch match = new NoCultureMatch();
            match.setRuleId(rule.getId());
            match.setBatchId(batch.getId());
            match.setPondId(pond == null ? batch.getPondId() : pond.getId());
            match.setResult(NoCultureMatch.RESULT_FAILED);
            match.setSpatialHit(true);
            match.setDetail("冻结失败：" + ex.getClass().getSimpleName() + "：" + ex.getMessage());
            match.setRequestId(requestId + "#E" + batch.getId());
            matchMapper.insert(match);
            evidenceService.recordStandalone(NoCultureChangeEvidence.ENTITY_MATCH, match.getId(),
                    NoCultureChangeEvidence.ACTION_FREEZE_FAILED, match.getRequestId(),
                    operator, null, match, "批次id=" + batch.getId() + "冻结失败，部分成功："
                            + ex.getClass().getName() + "：" + ex.getMessage());
        } catch (Exception ignored) {
            // 独立证据也失败时不应掩盖主流程；勾稽核对会暴露缺口。
        }
        result.failed++;
    }

    /**
     * 空间边界判定异常：绝不能静默当"不命中"放跑在途批。
     * 留 SPATIAL_FAILED 匹配，并以独立事务把规则、批次、池塘快照与异常类型/信息作为
     * 原始凭证保存（主事务回滚也不丢），等待人工核实边界后补冻。
     */
    private void insertSpatialFailure(NoCultureRule rule, SeedlingBatch batch, Pond pond,
                                      String operator, String requestId, Exception ex,
                                      ScanSummary result) {
        String detail = "空间边界判定异常，批次id=" + batch.getId() + "（" + batch.getBatchNo()
                + "，池id=" + batch.getPondId() + "）未能确认是否命中规则" + rule.getRuleNo()
                + "，按疑点批次挂起待人工核实补冻：" + ex.getClass().getName() + "：" + ex.getMessage();
        try {
            NoCultureMatch match = new NoCultureMatch();
            match.setRuleId(rule.getId());
            match.setBatchId(batch.getId());
            match.setPondId(pond == null ? batch.getPondId() : pond.getId());
            match.setResult(NoCultureMatch.RESULT_SPATIAL_FAILED);
            match.setSpatialHit(false);
            match.setDetail(detail);
            match.setRequestId(requestId + "#S" + batch.getId());
            matchMapper.insert(match);
            Map<String, Object> voucher = new LinkedHashMap<>();
            voucher.put("rule", rule);
            voucher.put("batch", batch);
            voucher.put("pond", pond);
            voucher.put("errorType", ex.getClass().getName());
            voucher.put("errorMessage", ex.getMessage());
            evidenceService.recordStandalone(NoCultureChangeEvidence.ENTITY_MATCH, match.getId(),
                    NoCultureChangeEvidence.ACTION_SPATIAL_FAILED, match.getRequestId(),
                    operator, null, voucher, detail);
        } catch (Exception ignored) {
            // 凭证落库再失败不应掩盖主流程；需人工介入核对扫描缺口。
        }
        result.spatialFailed++;
    }

    /** 其他仍 ACTIVE 且对该批保留 FROZEN 账的规则（空间重叠命中过）。 */
    private List<NoCultureFreeze> otherActiveFreezes(Long batchId, Long excludeRuleId) {
        List<NoCultureFreeze> frozen = freezeMapper.selectList(new QueryWrapper<NoCultureFreeze>()
                .eq("batch_id", batchId)
                .eq("status", NoCultureFreeze.STATUS_FROZEN)
                .ne("rule_id", excludeRuleId));
        List<NoCultureFreeze> active = new ArrayList<>();
        for (NoCultureFreeze freeze : frozen) {
            NoCultureRule other = ruleMapper.selectById(freeze.getRuleId());
            if (other != null && NoCultureRule.STATUS_ACTIVE.equals(other.getStatus())) {
                active.add(freeze);
            }
        }
        return active;
    }

    private String buildHitDetail(NoCultureRule rule, Pond pond) {
        if (NoCultureRule.SCOPE_AREA.equals(rule.getScopeType())) {
            return "整海区命中：池塘海区=" + pond.getSeaArea();
        }
        return "多边形命中：池塘坐标(" + pond.getLongitude() + "," + pond.getLatitude()
                + ")落入规则" + rule.getRuleNo() + "多边形范围";
    }

    private String normalizeScope(String scope) {
        if (NoCultureRule.SCOPE_POLYGON.equals(scope)) {
            return NoCultureRule.SCOPE_POLYGON;
        }
        if (NoCultureRule.SCOPE_AREA.equals(scope)) {
            return NoCultureRule.SCOPE_AREA;
        }
        throw new IllegalArgumentException("禁养范围类型只能是 AREA（整海区）或 POLYGON（多边形）");
    }

    /** 校验多边形顶点（至少3点、每点经纬度合法）并落标准 JSON。 */
    private String validatePolygon(List<double[]> polygon) {
        if (polygon == null || polygon.size() < 3) {
            throw new IllegalArgumentException("多边形禁养范围至少需要3个顶点");
        }
        for (double[] point : polygon) {
            if (point == null || point.length < 2) {
                throw new IllegalArgumentException("多边形顶点必须为[经度,纬度]");
            }
            if (point[0] < -180 || point[0] > 180 || point[1] < -90 || point[1] > 90) {
                throw new IllegalArgumentException("多边形顶点经纬度超出合法范围：经度[-180,180]、纬度[-90,90]");
            }
        }
        try {
            return objectMapper.writeValueAsString(polygon);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("多边形顶点序列化失败：" + ex.getMessage());
        }
    }

    private String joinRuleIds(List<NoCultureFreeze> freezes) {
        StringBuilder sb = new StringBuilder();
        for (NoCultureFreeze freeze : freezes) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(freeze.getRuleId());
        }
        return sb.toString();
    }

    private Map<String, Object> ruleView(NoCultureRule rule, ScanSummary summary, boolean idempotent) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("rule", rule);
        view.put("idempotent", idempotent);
        view.put("frozenCount", summary.frozen);
        view.put("completedNoticeCount", summary.completedNotice);
        view.put("failedCount", summary.failed);
        view.put("spatialFailedCount", summary.spatialFailed);
        view.put("existedCount", summary.existed);
        if (summary.detail != null) {
            view.put("detail", summary.detail);
        }
        return view;
    }

    private Map<String, Object> revokeView(NoCultureRule rule, ScanSummary summary, boolean idempotent) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("rule", rule);
        view.put("idempotent", idempotent);
        view.put("releasedCount", summary.released);
        view.put("skippedStillBoundCount", summary.skippedStillBound);
        return view;
    }

    private ScanSummary scanSummary(Long ruleId) {
        ScanSummary summary = new ScanSummary();
        summary.frozen = count(ruleId, NoCultureMatch.RESULT_FROZEN);
        summary.completedNotice = count(ruleId, NoCultureMatch.RESULT_COMPLETED_NOTICE);
        summary.failed = count(ruleId, NoCultureMatch.RESULT_FAILED);
        summary.spatialFailed = count(ruleId, NoCultureMatch.RESULT_SPATIAL_FAILED);
        return summary;
    }

    private int count(Long ruleId, String result) {
        Long c = matchMapper.selectCount(new QueryWrapper<NoCultureMatch>()
                .eq("rule_id", ruleId).eq("result", result));
        return c == null ? 0 : c.intValue();
    }

    private ScanSummary compensationSummary(Long ruleId) {
        ScanSummary summary = new ScanSummary();
        Long released = compensationMapper.selectCount(new QueryWrapper<NoCultureCompensation>()
                .eq("rule_id", ruleId).eq("result", NoCultureCompensation.RESULT_RELEASED));
        Long skipped = compensationMapper.selectCount(new QueryWrapper<NoCultureCompensation>()
                .eq("rule_id", ruleId).eq("result", NoCultureCompensation.RESULT_SKIPPED_STILL_BOUND));
        summary.released = released == null ? 0 : released.intValue();
        summary.skippedStillBound = skipped == null ? 0 : skipped.intValue();
        return summary;
    }

    private static class ScanSummary {
        int frozen;
        int completedNotice;
        int failed;
        int spatialFailed;
        int existed;
        int released;
        int skippedStillBound;
        String detail;
    }
}
