package com.evops.aqua.release.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.InspectionRecordMapper;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.release.dto.LegacyReplayRequest;
import com.evops.aqua.release.entity.LegacyPlayback;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.entity.ReleaseRuleVersion;
import com.evops.aqua.release.entity.ReleaseThresholdRule;
import com.evops.aqua.release.mapper.LegacyPlaybackMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海水养殖旧台账（aqua-1）数据只读回放：
 * 把旧的养殖池/投苗批次/抽检/出池数据投影到 aqua-13 的检验放行口径，
 * 逐条列出「旧口径 → 新口径」差异并给出可解释原因。全程只读，绝不修改任何旧数据。
 * requestId 幂等：重复回放返回首次结果。
 */
@Service
public class LegacyReplayService {

    private final PondMapper pondMapper;
    private final SeedlingBatchMapper batchMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final HarvestRecordMapper harvestMapper;
    private final LegacyPlaybackMapper playbackMapper;
    private final ReleaseRuleVersionService ruleVersionService;
    private final ReleaseEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public LegacyReplayService(PondMapper pondMapper,
                               SeedlingBatchMapper batchMapper,
                               InspectionRecordMapper inspectionMapper,
                               HarvestRecordMapper harvestMapper,
                               LegacyPlaybackMapper playbackMapper,
                               ReleaseRuleVersionService ruleVersionService,
                               ReleaseEvidenceService evidenceService,
                               ObjectMapper objectMapper) {
        this.pondMapper = pondMapper;
        this.batchMapper = batchMapper;
        this.inspectionMapper = inspectionMapper;
        this.harvestMapper = harvestMapper;
        this.playbackMapper = playbackMapper;
        this.ruleVersionService = ruleVersionService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public LegacyPlayback replay(LegacyReplayRequest request) {
        List<LegacyPlayback> same = playbackMapper.selectList(new QueryWrapper<LegacyPlayback>()
                .eq("request_id", request.getRequestId()));
        if (!same.isEmpty()) {
            return same.get(0);
        }

        List<Pond> ponds = pondMapper.selectList(new QueryWrapper<Pond>().orderByAsc("id"));
        QueryWrapper<SeedlingBatch> bw = new QueryWrapper<SeedlingBatch>().orderByAsc("id");
        bw.eq(request.getBatchId() != null, "id", request.getBatchId());
        List<SeedlingBatch> batches = batchMapper.selectList(bw);

        int stocked = 0;
        int harvested = 0;
        for (SeedlingBatch b : batches) {
            if (SeedlingBatch.STATUS_HARVESTED.equals(b.getStatus())) {
                harvested++;
            } else {
                stocked++;
            }
        }
        long inspectionTotal = inspectionMapper.selectCount(new QueryWrapper<>());
        long harvestTotal = harvestMapper.selectCount(new QueryWrapper<>());

        // 当前生效阈值版本（回放早于新版本发布时允许为空——这本身作为一条差异解释）
        ReleaseRuleVersion activeVersion = null;
        try {
            activeVersion = ruleVersionService.currentActive();
        } catch (IllegalArgumentException ignore) {
            activeVersion = null;
        }

        List<Map<String, Object>> batchFindings = new ArrayList<>();
        int comboMatched = 0;
        int comboMissed = 0;
        for (SeedlingBatch b : batches) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("batchId", b.getId());
            f.put("batchNo", b.getBatchNo());
            f.put("species", b.getSpecies());
            f.put("pondId", b.getPondId());
            f.put("legacyStatus", b.getStatus());

            long insCount = inspectionMapper.selectCount(new QueryWrapper<InspectionRecord>()
                    .eq("batch_id", b.getId()));
            long harCount = harvestMapper.selectCount(new QueryWrapper<HarvestRecord>()
                    .eq("batch_id", b.getId()));
            f.put("legacyInspectionCount", insCount);
            f.put("legacyHarvestCount", harCount);

            // 旧抽检只有长势/成活率/自由文本，没有逐项指标阈值、报告号、样品号
            List<InspectionRecord> inspections = inspectionMapper.selectList(
                    new QueryWrapper<InspectionRecord>().eq("batch_id", b.getId()).orderByAsc("id"));
            List<String> legacyResults = new ArrayList<>();
            for (InspectionRecord ir : inspections) {
                legacyResults.add(ir.getResult() == null ? "(无文字结论)" : ir.getResult());
            }
            f.put("legacyInspectionResults", legacyResults);

            // 新版本阈值组合命中情况（用途在旧数据中不存在，只能先按品种核对是否有任一用途配置）
            if (activeVersion != null) {
                List<ReleaseThresholdRule> speciesRules = ruleVersionService.listThresholds(activeVersion.getId())
                        .stream()
                        .filter(r -> r.getSpeciesCode().equals(speciesKey(b.getSpecies())))
                        .collect(java.util.stream.Collectors.toList());
                if (speciesRules.isEmpty()) {
                    comboMissed++;
                    f.put("thresholdMatched", false);
                    f.put("thresholdExplanation", "旧批次品种[" + b.getSpecies()
                            + "]在新版本中暂无任何用途的检验阈值组合，建检验单前需先在规则版本中补齐该品种的用途阈值");
                } else {
                    comboMatched++;
                    List<String> usages = new ArrayList<>();
                    for (ReleaseThresholdRule r : speciesRules) {
                        String label = r.getUsageCode() + "(" + r.getUsageName() + ")";
                        if (!usages.contains(label)) {
                            usages.add(label);
                        }
                    }
                    f.put("thresholdMatched", true);
                    f.put("availableUsages", usages);
                    f.put("thresholdExplanation", "旧批次品种[" + b.getSpecies()
                            + "]可匹配新版本用途组合：" + usages + "；旧数据不记录用途，建检验单时须指定用途");
                }
            } else {
                f.put("thresholdMatched", null);
                f.put("thresholdExplanation", "新版本尚未发布，暂无法按品种+用途匹配阈值；发布新版本后再回放可得到命中结果");
            }
            batchFindings.add(f);
        }

        List<Map<String, Object>> differences = buildGlobalDifferences(
                ponds.size(), batches.size(), inspectionTotal, harvestTotal,
                activeVersion, comboMatched, comboMissed);

        String findingsJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("batches", batchFindings);
            payload.put("differences", differences);
            findingsJson = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            findingsJson = "{\"error\":\"回放结论序列化失败\"}";
        }

        LegacyPlayback playback = new LegacyPlayback();
        playback.setRequestId(request.getRequestId());
        playback.setSourceVersion("aqua-1");
        playback.setPondCount(ponds.size());
        playback.setBatchCount(batches.size());
        playback.setStockedCount(stocked);
        playback.setHarvestedCount(harvested);
        playback.setInspectionCount((int) inspectionTotal);
        playback.setFindingsJson(findingsJson);
        playback.setDiffSummary(joinSummaries(differences));
        playback.setOperator(request.getOperator() == null
                ? ReleaseEvidenceService.currentOperator() : request.getOperator());
        playbackMapper.insert(playback);

        evidenceService.record(ReleaseChangeEvidence.ENTITY_PLAYBACK, playback.getId(),
                ReleaseChangeEvidence.ACTION_REPLAY, request.getRequestId(),
                playback.getOperator(), null, playback,
                "只读回放海水养殖旧台账(aqua-1)：池塘" + ponds.size() + "、批次" + batches.size()
                        + "（在养" + stocked + "/已出池" + harvested + "）、旧抽检" + inspectionTotal
                        + "、旧出池" + harvestTotal + "，差异" + differences.size() + "项均可解释；未修改任何旧数据");
        return playback;
    }

    public List<LegacyPlayback> list() {
        return playbackMapper.selectList(new QueryWrapper<LegacyPlayback>().orderByDesc("id"));
    }

    public LegacyPlayback getOrThrow(Long id) {
        LegacyPlayback p = playbackMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("旧数据回放记录不存在，id=" + id);
        }
        return p;
    }

    // ------------------------------------------------------------
    // 差异解释：每条给出 旧口径 / 新口径 / 原因
    // ------------------------------------------------------------

    private List<Map<String, Object>> buildGlobalDifferences(int pondCount, int batchCount,
                                                             long inspectionTotal, long harvestTotal,
                                                             ReleaseRuleVersion activeVersion,
                                                             int comboMatched, int comboMissed) {
        List<Map<String, Object>> diffs = new ArrayList<>();

        diffs.add(diff("D1 检验口径",
                "旧抽检 t_inspection_record 仅记录抽样数、平均体重、成活率与一段自由文本结论（共" + inspectionTotal + "条）",
                "新模块按「品种 + 用途」组合逐指标配置数值阈值，录入后系统按闭区间自动比对合格与否",
                "旧口径是长势观察、无法支撑出池放行的逐项合规判定；新版本把自由文本升级为可自动比对的结构化阈值"));

        diffs.add(diff("D2 放行方式",
                "旧数据无放行建议概念，抽检文本结论不与任何阈值挂钩，也无放行/拒收状态",
                "新模块录入结果后系统自动生成放行建议 PASS/FAIL 及逐项判定说明",
                "放行建议由阈值比对机械得出，避免人工目测放行；最终放行仍以授权签字人电子签名为准"));

        diffs.add(diff("D3 签名与发货门",
                "旧台账出池（t_harvest_record，共" + harvestTotal + "条）后即可视为完成，没有电子签名，也没有发货环节门",
                "新批次只有授权签字人工号+口令双因子电子签名（SIGNED）后，发货登记才被接受；未签名一律不能发货",
                "出池是养殖动作、发货是合规放行动作，二者分离；签名门保证未放行产品不流出"));

        diffs.add(diff("D4 样品与报告",
                "旧抽检无样品编号、无检测报告编号，结论直接挂在抽检记录上",
                "新模块每检验轮一个样品、一份检测报告，样品编号与报告编号严格一一对应",
                "满足检测报告与样品可双向追溯、报告不可被复检顶替的合规要求"));

        diffs.add(diff("D5 复检与退回",
                "旧抽检若要重检只能再新增一条抽检记录，原记录与重检记录之间无轮次与保留约定",
                "新模块初检/复检分轮追加，复检绝不覆盖初检；退回补检保留原结论、退回原因与操作时间",
                "保证初检事实不被复检结果抹除，退回链路可审计"));

        if (activeVersion == null) {
            diffs.add(diff("D6 阈值版本",
                    "旧数据无阈值规则版本概念",
                    "新版本尚未发布，本次回放暂不做品种+用途阈值命中判定",
                    "按要求先用旧数据回放、再提交新版本「批次出池检验与放行签名」；发布后重放即可得到每个旧品种的阈值命中解释"));
        } else {
            diffs.add(diff("D6 阈值版本",
                    "旧数据无阈值规则版本概念，品种为自由文本、且不记录用途",
                    "已发布生效版本" + activeVersion.getVersionNo() + "：旧品种命中阈值组合的批次"
                            + comboMatched + "个，未命中" + comboMissed + "个",
                    "用途是出池放行新增维度（如生食/熟食/种苗），旧台账不含该字段，故命中只按品种核对、用途在建单时指定；未命中品种需在规则版本中补齐阈值"));
        }

        diffs.add(diff("D7 对旧数据的影响",
                "旧台账表与数据保持原样",
                "回放只读投影，不回写、不改状态、不补单据；新版本检验单/发货需对在养批次另行建立",
                "已出池批次历史事实冻结不回改（与全系统“历史事实不可变”口径一致），仅在养批次可纳入新放行流程"));

        return diffs;
    }

    private Map<String, Object> diff(String code, String legacy, String current, String explanation) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("legacy", legacy);
        m.put("current", current);
        m.put("explanation", explanation);
        return m;
    }

    private String speciesKey(String species) {
        return species == null ? "" : species.trim();
    }

    private String joinSummaries(List<Map<String, Object>> diffs) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> d : diffs) {
            sb.append(d.get("code")).append("：").append(d.get("explanation")).append("；");
        }
        String s = sb.toString();
        return s.length() > 1900 ? s.substring(0, 1900) : s;
    }
}
