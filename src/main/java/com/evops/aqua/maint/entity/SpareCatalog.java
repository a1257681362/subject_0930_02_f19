package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 备件目录：备件品类与安全库存。带序列号的实物实例见 {@link SparePart}。
 */
@TableName("t_maint_spare_catalog")
public class SpareCatalog extends BaseEntity {

    private String partCode;
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
