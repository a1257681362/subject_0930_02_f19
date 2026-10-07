package com.evops.aqua.noculture.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 空间匹配记录：规则新增（或补扫）时对每个空间命中的批次留一条判定结果，只追加。
 * FROZEN 空间命中在途批次并冻结（与冻结账 freezeId 双向对应）；
 * COMPLETED_NOTICE 命中但批次已完成——只提示，绝不回写台账；
 * FAILED 单批冻结失败（部分成功），保留原因等待人工/补偿处理。
 * 禁养区域（规则）与空间匹配记录通过 ruleId 一一可勾稽，不允许悬挂。
 */
@TableName("t_noculture_match")
public class NoCultureMatch extends BaseEntity {

    public static final String RESULT_FROZEN = "FROZEN";
    public static final String RESULT_COMPLETED_NOTICE = "COMPLETED_NOTICE";
    public static final String RESULT_FAILED = "FAILED";
    /** 空间边界判定本身异常（无法确认是否命中）：不能静默当不命中，留痕待人工核实/补冻。 */
    public static final String RESULT_SPATIAL_FAILED = "SPATIAL_FAILED";

    private Long ruleId;
    private Long batchId;
    private Long pondId;
    private String result;
    private Long freezeId;
    private Boolean spatialHit;
    private String detail;
    private String requestId;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Long getFreezeId() { return freezeId; }
    public void setFreezeId(Long freezeId) { this.freezeId = freezeId; }
    public Boolean getSpatialHit() { return spatialHit; }
    public void setSpatialHit(Boolean spatialHit) { this.spatialHit = spatialHit; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
