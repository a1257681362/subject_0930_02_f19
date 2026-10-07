package com.evops.aqua.med.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 领用明细行：一种药品一个批号；dosagePerM3 为空时取药品档案标准剂量。
 */
public class RequisitionItemRequest {

    @NotNull(message = "药品不能为空")
    private Long drugId;

    @NotNull(message = "药品批号不能为空")
    private Long medBatchId;

    @DecimalMin(value = "0.0001", message = "剂量必须大于0")
    private BigDecimal dosagePerM3;

    public Long getDrugId() { return drugId; }
    public void setDrugId(Long drugId) { this.drugId = drugId; }
    public Long getMedBatchId() { return medBatchId; }
    public void setMedBatchId(Long medBatchId) { this.medBatchId = medBatchId; }
    public BigDecimal getDosagePerM3() { return dosagePerM3; }
    public void setDosagePerM3(BigDecimal dosagePerM3) { this.dosagePerM3 = dosagePerM3; }
}
