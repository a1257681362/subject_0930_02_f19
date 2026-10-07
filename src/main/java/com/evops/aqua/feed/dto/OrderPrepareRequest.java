package com.evops.aqua.feed.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 投喂单准备请求：按池按批按日，依据苗种、日龄、水温从当前生效配方生成投喂明细。
 * 日龄由投苗日期与投喂日期推算，不由前端填写，避免篡改。
 */
public class OrderPrepareRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "投喂日期不能为空")
    private LocalDate feedDate;

    @NotNull(message = "水温不能为空")
    @DecimalMin(value = "0", message = "水温不能为负")
    @DecimalMax(value = "40", message = "水温超出合理范围")
    private BigDecimal waterTempC;

    /** TRIAL=试喂，FORMAL=正式投喂；为空按正式投喂。 */
    private String feedType;

    private String remark;

    private String requestId;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getFeedDate() { return feedDate; }
    public void setFeedDate(LocalDate feedDate) { this.feedDate = feedDate; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String feedType) { this.feedType = feedType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
