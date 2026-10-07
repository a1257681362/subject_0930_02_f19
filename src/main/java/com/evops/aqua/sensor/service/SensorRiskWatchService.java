package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.mapper.SensorChangeEvidenceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海水养殖管理高风险观察项（持续观察）。
 * 观察项「传感器补传造成日报重复」：离线归并键/日报汇总链路每次拦截到
 * 重复槽位补传、已审核区间补入或隔离包混入时，只追加 WATCH_GUARD 记录，
 * 不覆盖任何已有凭证；观察台可据此持续核对防护是否生效。
 */
@Service
public class SensorRiskWatchService {

    /** 观察项：传感器补传造成日报重复（溶氧最低值/采样次数被放大）。 */
    public static final String WATCH_RETRANSMIT_DUPLICATE = "SENSOR_RETRANSMIT_DUPLICATE";

    /** 观察项在证据表中的固定实体 id（观察项为域级常量，不单独建表）。 */
    public static final long WATCH_ENTITY_ID = 9001L;

    public static final String LEVEL_HIGH = "HIGH";
    public static final String STATUS_WATCHING = "WATCHING";

    private final SensorEvidenceService evidenceService;
    private final SensorChangeEvidenceMapper evidenceMapper;

    public SensorRiskWatchService(SensorEvidenceService evidenceService,
                                  SensorChangeEvidenceMapper evidenceMapper) {
        this.evidenceService = evidenceService;
        this.evidenceMapper = evidenceMapper;
    }

    /**
     * 追加一条观察防护记录。只追加：before 恒为 null，绝不修改/覆盖既有凭证。
     */
    public void recordGuard(String requestId, Object snapshot, String detail) {
        evidenceService.record(SensorChangeEvidence.ENTITY_RISK_WATCH, WATCH_ENTITY_ID,
                SensorChangeEvidence.ACTION_WATCH_GUARD, requestId, null, snapshot, detail);
    }

    /**
     * 高风险观察台：观察项定义 + 防护命中次数与最近记录。
     */
    public List<Map<String, Object>> watchItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item(WATCH_RETRANSMIT_DUPLICATE, "传感器补传造成日报重复",
                "设备重复补传后日报溶氧最低值与采样次数被重复放大",
                "归并自然键=（池塘,指标,设备时间,序号），沿设备更换关系识别同一物理采样；"
                        + "日报仅由 NORMAL/CONFIRMED 归并读数重算；迟到数据只补未审核区间；"
                        + "签名失败/未知设备整包隔离不进汇总；异常时证据只追加不覆盖凭证"));
        return items;
    }

    private Map<String, Object> item(String code, String name, String risk, String control) {
        List<SensorChangeEvidence> guards = evidenceMapper.selectList(
                new QueryWrapper<SensorChangeEvidence>()
                        .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                        .eq("entity_id", WATCH_ENTITY_ID)
                        .eq("action", SensorChangeEvidence.ACTION_WATCH_GUARD)
                        .orderByDesc("id").last("limit 10"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("watchCode", code);
        item.put("watchName", name);
        item.put("scope", "海水养殖管理");
        item.put("riskLevel", LEVEL_HIGH);
        item.put("status", STATUS_WATCHING);
        item.put("risk", risk);
        item.put("relatedControl", control);
        item.put("guardCount", guardCount());
        item.put("latestGuards", guards);
        return item;
    }

    private long guardCount() {
        Long count = evidenceMapper.selectCount(new QueryWrapper<SensorChangeEvidence>()
                .eq("entity_type", SensorChangeEvidence.ENTITY_RISK_WATCH)
                .eq("entity_id", WATCH_ENTITY_ID)
                .eq("action", SensorChangeEvidence.ACTION_WATCH_GUARD));
        return count == null ? 0L : count;
    }
}
