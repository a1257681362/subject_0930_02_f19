package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 备件目录登记请求。
 */
public class SpareCatalogRequest {

    @NotBlank(message = "备件编码不能为空")
    private String partCode;
    @NotBlank(message = "备件名称不能为空")
    private String partName;
    private String spec;
    private Integer safetyStock;

    public String getPartCode() { return partCode; }
    public void setPartCode(String partCode) { this.partCode = partCode; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public Integer getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Integer safetyStock) { this.safetyStock = safetyStock; }
}
