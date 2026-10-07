package com.evops.aqua.noculture.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 批次冻结账：一条规则 × 一个在途批次一行（uk rule_id+batch_id）。
 * 批次存在任意 FROZEN 行即在途冻结——投苗/转池/捕捞三入口一律拦截。
 * 撤销规则时逐行补偿解冻：仍被其他重叠生效规则冻结的行保持 FROZEN（补偿记 SKIPPED_STILL_BOUND），
 * 其余置 RELEASED。冻结/解冻全程留证。
 */
@TableName("t_noculture_freeze")
public class NoCultureFreeze extends BaseEntity {

    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_RELEASED = "RELEASED";

    private Long ruleId;
    private Long batchId;
    private Long pondId;
    private Long matchId;
    private String status;
    private Integer strictLevel;
    private String hitDetail;
    private String frozenBy;
    private LocalDateTime frozenTime;
    private String unfrozenBy;
    private LocalDateTime unfrozenTime;
    private String requestId;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStrictLevel() { return strictLevel; }
    public void setStrictLevel(Integer strictLevel) { this.strictLevel = strictLevel; }
    public String getHitDetail() { return hitDetail; }
    public void setHitDetail(String hitDetail) { this.hitDetail = hitDetail; }
    public String getFrozenBy() { return frozenBy; }
    public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }
    public LocalDateTime getFrozenTime() { return frozenTime; }
    public void setFrozenTime(LocalDateTime frozenTime) { this.frozenTime = frozenTime; }
    public String getUnfrozenBy() { return unfrozenBy; }
    public void setUnfrozenBy(String unfrozenBy) { this.unfrozenBy = unfrozenBy; }
    public LocalDateTime getUnfrozenTime() { return unfrozenTime; }
    public void setUnfrozenTime(LocalDateTime unfrozenTime) { this.unfrozenTime = unfrozenTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
