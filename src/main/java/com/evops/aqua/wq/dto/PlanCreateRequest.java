package com.evops.aqua.wq.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建水质采样计划：绑定规则版本与养殖阶段，按所选池塘生成采样任务。
 * ruleVersionId 为空时使用当前生效（ACTIVE）版本。
 */
public class PlanCreateRequest {

    @NotBlank(message = "计划编号不能为空")
    private String planNo;
    @NotBlank(message = "计划名称不能为空")
    private String planName;
    private Long ruleVersionId;
    @NotBlank(message = "适用养殖阶段不能为空")
    private String stageCode;
    @NotNull(message = "计划开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "计划结束日期不能为空")
    private LocalDate endDate;
    @NotEmpty(message = "至少选择一个养殖池")
    private List<Long> pondIds;
    private String remark;

    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getStageCode() { return stageCode; }
    public void setStageCode(String stageCode) { this.stageCode = stageCode; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public List<Long> getPondIds() { return pondIds; }
    public void setPondIds(List<Long> pondIds) { this.pondIds = pondIds; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
