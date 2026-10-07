package com.evops.aqua.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.report.dto.CaliberIndicatorInput;
import com.evops.aqua.report.dto.CaliberPublishRequest;
import com.evops.aqua.report.entity.ReportCaliberIndicator;
import com.evops.aqua.report.entity.ReportCaliberVersion;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.mapper.ReportCaliberIndicatorMapper;
import com.evops.aqua.report.mapper.ReportCaliberVersionMapper;
import com.evops.aqua.report.mapper.ReportChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月报统计口径版本管理。
 * - 系统预置 V1 默认口径（首版由系统在首次发布/生成时自动建立，不属于用户管理动作）；
 * - 发布只能新增版本：旧 ACTIVE 版本置 SUPERSEDED 冻结，版本与其逐指标口径一律不可改；
 * - 月报生成只引用当时 ACTIVE 版本并固化进快照，之后发新版本不回改任何历史快照；
 * - requestId 幂等：重复发布请求返回首次版本。
 */
@Service
public class CaliberVersionService {

    public static final String IND_SEEDLING = "SEEDLING";
    public static final String IND_MEDICATION = "MEDICATION";
    public static final String IND_MORTALITY = "MORTALITY";
    public static final String IND_HARVEST = "HARVEST";
    public static final String IND_INSPECTION = "INSPECTION";

    public static final List<String> INDICATOR_CODES = java.util.Arrays.asList(
            IND_SEEDLING, IND_MEDICATION, IND_MORTALITY, IND_HARVEST, IND_INSPECTION);

    private final ReportCaliberVersionMapper versionMapper;
    private final ReportCaliberIndicatorMapper indicatorMapper;
    private final ReportChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public CaliberVersionService(ReportCaliberVersionMapper versionMapper,
                                 ReportCaliberIndicatorMapper indicatorMapper,
                                 ReportChangeEvidenceMapper evidenceMapper,
                                 ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.indicatorMapper = indicatorMapper;
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    /** 取当前生效版本；不存在时系统自动建立 V1 默认口径（系统首版，不走缺权闸门）。 */
    @Transactional(rollbackFor = Exception.class)
    public ReportCaliberVersion getOrCreateDefault() {
        ReportCaliberVersion active = activeVersion();
        if (active != null) {
            return active;
        }
        return createVersion(new LinkedHashMap<String, Object>(), "system",
                "系统首版默认统计口径", ReportChangeEvidence.ACTION_PUBLISH, null);
    }

    /**
     * 发布新口径版本（调用方必须先过缺权闸门 ReportAccessService.requireCaliberManageAllowed）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportCaliberVersion publish(CaliberPublishRequest request, String operator) {
        if (request != null && request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            ReportCaliberVersion existing = findByPublishRequestId(request.getRequestId());
            if (existing != null) {
                return existing;
            }
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (request != null && request.getIndicators() != null) {
            for (CaliberIndicatorInput input : request.getIndicators()) {
                if (input == null || input.getIndicatorCode() == null) {
                    throw new IllegalArgumentException("指标口径项缺少 indicatorCode");
                }
                if (!INDICATOR_CODES.contains(input.getIndicatorCode())) {
                    throw new IllegalArgumentException("未知月报指标：" + input.getIndicatorCode());
                }
                overrides.put(input.getIndicatorCode(), input.getCaliber());
            }
        }
        String remark = request == null ? null : request.getRemark();
        return createVersion(overrides, operator, remark,
                ReportChangeEvidence.ACTION_PUBLISH,
                request == null ? null : request.getRequestId());
    }

    public ReportCaliberVersion activeVersion() {
        return versionMapper.selectOne(new QueryWrapper<ReportCaliberVersion>()
                .eq("status", ReportCaliberVersion.STATUS_ACTIVE).last("limit 1"));
    }

    public List<ReportCaliberVersion> listVersions() {
        return versionMapper.selectList(new QueryWrapper<ReportCaliberVersion>()
                .orderByDesc("id"));
    }

    public List<ReportCaliberIndicator> listIndicators(Long versionId) {
        return indicatorMapper.selectList(new QueryWrapper<ReportCaliberIndicator>()
                .eq("version_id", versionId).orderByAsc("id"));
    }

    public ReportCaliberVersion requireVersion(Long versionId) {
        ReportCaliberVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("统计口径版本不存在：" + versionId);
        }
        return version;
    }

    private ReportCaliberVersion createVersion(Map<String, Object> overrides, String operator,
                                               String remark, String action, String requestId) {
        ReportCaliberVersion previous = activeVersion();
        long seq = versionMapper.selectCount(new QueryWrapper<>()) + 1;
        ReportCaliberVersion version = new ReportCaliberVersion();
        version.setVersionNo("CAL-V" + seq);
        version.setStatus(ReportCaliberVersion.STATUS_ACTIVE);
        version.setRemark(remark);
        version.setPublishedBy(operator);
        version.setPublishedAt(LocalDateTime.now());
        versionMapper.insert(version);

        if (previous != null) {
            previous.setStatus(ReportCaliberVersion.STATUS_SUPERSEDED);
            versionMapper.updateById(previous);
        }

        for (String code : INDICATOR_CODES) {
            ReportCaliberIndicator row = new ReportCaliberIndicator();
            row.setVersionId(version.getId());
            row.setIndicatorCode(code);
            row.setIndicatorName(defaultName(code));
            row.setCaliberJson(buildCaliberJson(code, overrides.get(code)));
            indicatorMapper.insert(row);
        }

        ReportChangeEvidence evidence = new ReportChangeEvidence();
        evidence.setEntityType(ReportChangeEvidence.ENTITY_CALIBER);
        evidence.setEntityId(version.getId());
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setOperator(operator);
        evidence.setBeforeJson(previous == null ? null : ("{\"versionNo\":\"" + previous.getVersionNo() + "\"}"));
        evidence.setAfterJson("{\"versionNo\":\"" + version.getVersionNo() + "\",\"indicators\":" + INDICATOR_CODES.size() + "}");
        evidence.setDetail("发布月报统计口径版本" + version.getVersionNo()
                + (previous == null ? "（首版）" : "，旧版本" + previous.getVersionNo() + "冻结为SUPERSEDED")
                + (remark == null ? "" : "，备注=" + remark));
        evidenceMapper.insert(evidence);
        return version;
    }

    private ReportCaliberVersion findByPublishRequestId(String requestId) {
        ReportChangeEvidence evidence = evidenceMapper.selectOne(
                new QueryWrapper<ReportChangeEvidence>()
                        .eq("entity_type", ReportChangeEvidence.ENTITY_CALIBER)
                        .eq("action", ReportChangeEvidence.ACTION_PUBLISH)
                        .eq("request_id", requestId)
                        .last("limit 1"));
        return evidence == null ? null : versionMapper.selectById(evidence.getEntityId());
    }

    private String buildCaliberJson(String code, Object override) {
        Map<String, Object> caliber = defaultCaliber(code);
        if (override != null) {
            if (override instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> merged = (Map<String, Object>) override;
                caliber.putAll(merged);
            }
            caliber.put("customOverride", override);
        }
        caliber.put("indicatorCode", code);
        caliber.put("indicatorName", defaultName(code));
        try {
            return objectMapper.writeValueAsString(caliber);
        } catch (Exception ex) {
            throw new IllegalArgumentException("指标口径序列化失败：" + ex.getMessage());
        }
    }

    public static String defaultName(String code) {
        switch (code) {
            case IND_SEEDLING: return "投苗量";
            case IND_MEDICATION: return "用药量";
            case IND_MORTALITY: return "死亡率";
            case IND_HARVEST: return "出池量";
            case IND_INSPECTION: return "检验结论";
            default: throw new IllegalArgumentException("未知月报指标：" + code);
        }
    }

    /** V1 起各指标默认统计口径（固化文本，随版本保存；调整只能发新版本）。 */
    private Map<String, Object> defaultCaliber(String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (code) {
            case IND_SEEDLING:
                m.put("unit", "尾");
                m.put("sourceTable", "t_seedling_batch");
                m.put("dateField", "stock_date");
                m.put("aggregation", "SUM(quantity)，按投苗日落入报告月、按池塘所属海区汇总");
                m.put("sampleEntity", "投苗批次");
                m.put("missingRule", "报告月内该海区无任何投苗批次，指标置 PENDING_FILL，不填零");
                break;
            case IND_MEDICATION:
                m.put("unit", "按药品各自计量单位");
                m.put("sourceTable", "t_med_requisition + t_med_requisition_item");
                m.put("dateField", "t_med_requisition.use_date");
                m.put("aggregation", "仅统计 APPROVED 已审核领用单，SUM(item.estimated_qty)");
                m.put("sampleEntity", "已审核用药领用单");
                m.put("missingRule", "报告月内无已审核领用单，指标置 PENDING_FILL，不填零；草稿/待审核/驳回单不计入");
                break;
            case IND_MORTALITY:
                m.put("unit", "%");
                m.put("sourceTable", "t_daily_record + t_seedling_batch");
                m.put("dateField", "record_date");
                m.put("aggregation", "SUM(daily.mortality_count) / 涉及批次当前在养数量合计 × 100，保留两位小数");
                m.put("sampleEntity", "日常记录（死亡登记）");
                m.put("missingRule", "报告月内无任何死亡登记日常记录，指标置 PENDING_FILL，不填零");
                break;
            case IND_HARVEST:
                m.put("unit", "尾");
                m.put("sourceTable", "t_harvest_record");
                m.put("dateField", "harvest_date");
                m.put("aggregation", "SUM(quantity)，按出池日落入报告月、按池塘所属海区汇总");
                m.put("sampleEntity", "出池记录");
                m.put("missingRule", "报告月内无出池记录，指标置 PENDING_FILL，不填零");
                break;
            case IND_INSPECTION:
                m.put("unit", "笔");
                m.put("sourceTable", "t_inspection_record + t_rel_round（已提交放行检验轮次）");
                m.put("dateField", "inspect_date / submitted_time");
                m.put("aggregation", "逐笔结论原样固化，汇总为 合格/不合格/其他 笔数，不做数值合计");
                m.put("sampleEntity", "抽检记录与放行检验轮次");
                m.put("missingRule", "报告月内两类检验均无记录，指标置 PENDING_FILL，不填零");
                break;
            default:
                throw new IllegalArgumentException("未知月报指标：" + code);
        }
        return m;
    }

    /** 生成快照时取指标口径行（随快照再次固化）。 */
    public List<ReportCaliberIndicator> indicatorsOfVersion(Long versionId) {
        return listIndicators(versionId);
    }

    /** 仅取各指标口径 JSON 的精简清单（详情/导出复用）。 */
    public Map<String, String> caliberJsonMap(Long versionId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ReportCaliberIndicator row : listIndicators(versionId)) {
            map.put(row.getIndicatorCode(), row.getCaliberJson());
        }
        return map;
    }

    public List<String> indicatorCodes() {
        return new ArrayList<>(INDICATOR_CODES);
    }
}
