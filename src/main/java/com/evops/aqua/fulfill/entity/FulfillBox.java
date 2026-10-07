package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 履约箱：捕捞后进入冷藏库的最小物流单元，箱号全局唯一。
 * 入库三要素：入库温度 intakeTemp、箱号 boxNo、称重结果（毛重 gross / 皮重 tare，净重服务端 = gross - tare）。
 * IN_STORE 在库；交接正常箱 → HANDED_OVER 出库扣重；净重差异超规则 → FROZEN 冻结在库（终态，不出库）。
 */
@TableName("t_fulfill_box")
public class FulfillBox extends BaseEntity {

    public static final String STATUS_IN_STORE = "IN_STORE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_HANDED_OVER = "HANDED_OVER";

    private String boxNo;
    private Long deliveryId;
    private Long orderId;
    private Long coldStoreId;
    private Long reservationId;
    private String specCode;
    private BigDecimal intakeTemp;
    private BigDecimal grossKg;
    private BigDecimal tareKg;
    private BigDecimal netKg;
    private String status;
    private Long handoverId;
    private BigDecimal handoverNetKg;
    private BigDecimal netDiffKg;
    private Boolean overTolerance;
    private String weighedBy;
    private LocalDateTime weighedTime;
    private String requestId;
    private String remark;

    public String getBoxNo() { return boxNo; }
    public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
    public Long getDeliveryId() { return deliveryId; }
    public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getColdStoreId() { return coldStoreId; }
    public void setColdStoreId(Long coldStoreId) { this.coldStoreId = coldStoreId; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getIntakeTemp() { return intakeTemp; }
    public void setIntakeTemp(BigDecimal intakeTemp) { this.intakeTemp = intakeTemp; }
    public BigDecimal getGrossKg() { return grossKg; }
    public void setGrossKg(BigDecimal grossKg) { this.grossKg = grossKg; }
    public BigDecimal getTareKg() { return tareKg; }
    public void setTareKg(BigDecimal tareKg) { this.tareKg = tareKg; }
    public BigDecimal getNetKg() { return netKg; }
    public void setNetKg(BigDecimal netKg) { this.netKg = netKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHandoverId() { return handoverId; }
    public void setHandoverId(Long handoverId) { this.handoverId = handoverId; }
    public BigDecimal getHandoverNetKg() { return handoverNetKg; }
    public void setHandoverNetKg(BigDecimal handoverNetKg) { this.handoverNetKg = handoverNetKg; }
    public BigDecimal getNetDiffKg() { return netDiffKg; }
    public void setNetDiffKg(BigDecimal netDiffKg) { this.netDiffKg = netDiffKg; }
    public Boolean getOverTolerance() { return overTolerance; }
    public void setOverTolerance(Boolean overTolerance) { this.overTolerance = overTolerance; }
    public String getWeighedBy() { return weighedBy; }
    public void setWeighedBy(String weighedBy) { this.weighedBy = weighedBy; }
    public LocalDateTime getWeighedTime() { return weighedTime; }
    public void setWeighedTime(LocalDateTime weighedTime) { this.weighedTime = weighedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
