package com.evops.aqua.noculture.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 海区禁养规则：监管人员维护禁养范围、生效时间与依据文件。
 * scope_type=AREA 按整海区（sea_area 相等即空间命中）；
 * scope_type=POLYGON 按 polygon_json 经纬度顶点围成的多边形（点-多边形判定）。
 * 时间一律按半开区间 [effectiveFrom, effectiveTo)，effectiveTo 为空表示长期有效。
 * strictLevel 越大越严格，空间边界重叠命中多条时三入口按最严格规则裁决。
 * 规则只允许新增与撤销（REVOKED），不提供删除——依据文件与冻结历史必须可追溯。
 */
@TableName("t_noculture_rule")
public class NoCultureRule extends BaseEntity {

    public static final String SCOPE_AREA = "AREA";
    public static final String SCOPE_POLYGON = "POLYGON";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    private String ruleNo;
    private String seaArea;
    private String scopeType;
    private String polygonJson;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String basisDocNo;
    private String basisDocName;
    private Integer strictLevel;
    private String status;
    /** 规则发布时点：冻结清单以此为基准在发布事务内生成。 */
    private LocalDateTime publishedTime;
    private String revokedBy;
    private LocalDateTime revokedTime;
    private String revokeReason;
    private String operator;
    private String requestId;
    private String remark;

    public String getRuleNo() { return ruleNo; }
    public void setRuleNo(String ruleNo) { this.ruleNo = ruleNo; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getPolygonJson() { return polygonJson; }
    public void setPolygonJson(String polygonJson) { this.polygonJson = polygonJson; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getBasisDocNo() { return basisDocNo; }
    public void setBasisDocNo(String basisDocNo) { this.basisDocNo = basisDocNo; }
    public String getBasisDocName() { return basisDocName; }
    public void setBasisDocName(String basisDocName) { this.basisDocName = basisDocName; }
    public Integer getStrictLevel() { return strictLevel; }
    public void setStrictLevel(Integer strictLevel) { this.strictLevel = strictLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public LocalDateTime getRevokedTime() { return revokedTime; }
    public void setRevokedTime(LocalDateTime revokedTime) { this.revokedTime = revokedTime; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
