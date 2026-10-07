package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 箱入库请求：捕捞后进入冷藏库逐箱登记。
 * 入库三要素——入库温度、箱号、称重结果（毛重/皮重，净重由服务端=毛重-皮重）。
 * requestId 幂等；箱号全局唯一。
 */
public class BoxIntakeRequest {

    @NotBlank(message = "箱号不能为空")
    private String boxNo;

    @NotNull(message = "冷藏库不能为空")
    private Long coldStoreId;

    @NotNull(message = "入库温度不能为空")
    private BigDecimal intakeTemp;

    @NotNull(message = "毛重不能为空")
    private BigDecimal grossKg;

    @NotNull(message = "皮重不能为空")
    private BigDecimal tareKg;

    @NotBlank(message = "称重人不能为空")
    private String weighedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getBoxNo() { return boxNo; }
    public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
    public Long getColdStoreId() { return coldStoreId; }
    public void setColdStoreId(Long coldStoreId) { this.coldStoreId = coldStoreId; }
    public BigDecimal getIntakeTemp() { return intakeTemp; }
    public void setIntakeTemp(BigDecimal intakeTemp) { this.intakeTemp = intakeTemp; }
    public BigDecimal getGrossKg() { return grossKg; }
    public void setGrossKg(BigDecimal grossKg) { this.grossKg = grossKg; }
    public BigDecimal getTareKg() { return tareKg; }
    public void setTareKg(BigDecimal tareKg) { this.tareKg = tareKg; }
    public String getWeighedBy() { return weighedBy; }
    public void setWeighedBy(String weighedBy) { this.weighedBy = weighedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
