package com.evops.aqua.noculture.dto;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 禁养规则登记请求（requestId 强制幂等）。
 * scopeType=AREA：按 seaArea 整海区禁养，polygon 不传；
 * scopeType=POLYGON：按 polygon 经纬度顶点围成的多边形禁养，seaArea 仍登记用于归口，
 * polygon 至少 3 个顶点，每点 [经度, 纬度]。
 * 生效时间按半开区间 [effectiveFrom, effectiveTo)，effectiveTo 可空（长期有效）。
 */
public class RuleCreateRequest {

    @NotBlank(message = "规则编号不能为空")
    @Size(max = 64, message = "规则编号最长64位")
    private String ruleNo;

    @Size(max = 64, message = "所属海区最长64位")
    private String seaArea;

    @NotBlank(message = "禁养范围类型不能为空（AREA/POLYGON）")
    private String scopeType;

    /** 多边形顶点，按顺序闭合；每点 [经度, 纬度]。scopeType=POLYGON 时必填，至少3点。 */
    @Valid
    private List<double[]> polygon;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @NotBlank(message = "依据文件文号不能为空")
    @Size(max = 128, message = "依据文件文号最长128位")
    private String basisDocNo;

    @NotBlank(message = "依据文件名称不能为空")
    @Size(max = 255, message = "依据文件名称最长255位")
    private String basisDocName;

    /** 严格程度，越大越严；空间边界重叠命中多条时按最严格规则执行。默认1。 */
    @Min(value = 1, message = "严格级别必须大于0")
    private Integer strictLevel;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    private String remark;

    public String getRuleNo() { return ruleNo; }
    public void setRuleNo(String ruleNo) { this.ruleNo = ruleNo; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public List<double[]> getPolygon() { return polygon; }
    public void setPolygon(List<double[]> polygon) { this.polygon = polygon; }
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
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
