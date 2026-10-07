package com.evops.aqua.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饲料投喂请求：按池按批留痕。
 */
public class FeedRecordRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "投喂日期不能为空")
    private LocalDate feedDate;

    @NotBlank(message = "饲料名称不能为空")
    private String feedName;

    @NotNull(message = "投喂量不能为空")
    @DecimalMin(value = "0.01", message = "投喂量必须大于0")
    private BigDecimal quantityKg;

    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getFeedDate() { return feedDate; }
    public void setFeedDate(LocalDate feedDate) { this.feedDate = feedDate; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
