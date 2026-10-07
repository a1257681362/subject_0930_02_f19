package com.evops.aqua.med.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 药品档案：标准剂量（每立方米水体用量）与休药期天数。
 * 领用单建单时把剂量与休药期冻结为快照，档案后续调整不影响历史单据。
 */
@TableName("t_med_drug")
public class MedDrug extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String drugCode;
    private String drugName;
    private String spec;
    private String unit;
    private BigDecimal dosagePerM3;
    private Integer withdrawalDays;
    private String status;
    private String requestId;
    private String remark;

    public String getDrugCode() { return drugCode; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getDosagePerM3() { return dosagePerM3; }
    public void setDosagePerM3(BigDecimal dosagePerM3) { this.dosagePerM3 = dosagePerM3; }
    public Integer getWithdrawalDays() { return withdrawalDays; }
    public void setWithdrawalDays(Integer withdrawalDays) { this.withdrawalDays = withdrawalDays; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
