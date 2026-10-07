package com.evops.aqua.feed.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饲料批号入库请求：一批饲料登记批号、数量与保质期。
 */
public class FeedBatchStockRequest {

    @NotBlank(message = "饲料批号不能为空")
    private String batchNo;

    @NotBlank(message = "饲料名称不能为空")
    private String feedName;

    private String supplier;

    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.01", message = "入库数量必须大于0")
    private BigDecimal quantityIn;

    private LocalDate productionDate;

    @NotNull(message = "到期日期不能为空")
    private LocalDate expiryDate;

    private String remark;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public BigDecimal getQuantityIn() { return quantityIn; }
    public void setQuantityIn(BigDecimal quantityIn) { this.quantityIn = quantityIn; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
