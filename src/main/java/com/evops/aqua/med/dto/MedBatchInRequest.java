package com.evops.aqua.med.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品批号入库请求：requestId 强制幂等，operator 落证据。
 */
public class MedBatchInRequest {

    @NotBlank(message = "批号不能为空")
    private String batchNo;

    @NotNull(message = "药品不能为空")
    private Long drugId;

    private String supplier;

    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.01", message = "入库数量必须大于0")
    private BigDecimal quantityIn;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Long getDrugId() { return drugId; }
    public void setDrugId(Long drugId) { this.drugId = drugId; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public BigDecimal getQuantityIn() { return quantityIn; }
    public void setQuantityIn(BigDecimal quantityIn) { this.quantityIn = quantityIn; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
