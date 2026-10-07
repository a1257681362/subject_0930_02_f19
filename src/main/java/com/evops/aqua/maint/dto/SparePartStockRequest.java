package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 备件实物入库请求：一物一序列号，同序列号重复入库返回首次实例。
 */
public class SparePartStockRequest {

    @NotNull(message = "备件品类不能为空")
    private Long catalogId;
    @NotBlank(message = "备件序列号不能为空")
    private String serialNo;

    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
}
