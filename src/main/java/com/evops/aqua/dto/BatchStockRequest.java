package com.evops.aqua.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 投苗请求：生成原始投苗单（批次编号全局唯一）。
 * biomassKg 为投苗时估计生物量，可空（默认0），后续可按抽检核定更新。
 */
public class BatchStockRequest {

    @NotBlank(message = "批次编号不能为空")
    @Size(max = 32, message = "批次编号最长32位")
    private String batchNo;

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotBlank(message = "品种不能为空")
    @Size(max = 64, message = "品种最长64位")
    private String species;

    @NotBlank(message = "苗种来源不能为空")
    @Size(max = 128, message = "苗种来源最长128位")
    private String seedSource;

    @NotNull(message = "入池数量不能为空")
    @Min(value = 1, message = "入池数量必须大于0")
    private Integer quantity;

    @Min(value = 0, message = "生物量不能为负")
    private BigDecimal biomassKg;

    @NotNull(message = "投苗日期不能为空")
    private LocalDate stockDate;

    @NotNull(message = "计划出池日不能为空")
    private LocalDate plannedHarvestDate;

    private String remark;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getSeedSource() { return seedSource; }
    public void setSeedSource(String seedSource) { this.seedSource = seedSource; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public LocalDate getStockDate() { return stockDate; }
    public void setStockDate(LocalDate stockDate) { this.stockDate = stockDate; }
    public LocalDate getPlannedHarvestDate() { return plannedHarvestDate; }
    public void setPlannedHarvestDate(LocalDate plannedHarvestDate) { this.plannedHarvestDate = plannedHarvestDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
