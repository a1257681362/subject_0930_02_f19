package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 捕捞现场分仓记录：实际均重按当时生效分级规则判级（gradingRuleId 冻结），
 * 入实际规格对应库位。requestId 唯一——补录/重试只形成一条可用记录，不重复加账。
 */
@TableName("t_fishing_load")
public class FishingLoad extends BaseEntity {

    private String loadNo;
    private Long reservationId;
    private Long orderId;
    private Long binId;
    private String specCode;
    private BigDecimal actualAvgWeightG;
    private BigDecimal weightKg;
    private Long gradingRuleId;
    private String loadedBy;
    private String requestId;
    private String remark;

    public String getLoadNo() { return loadNo; }
    public void setLoadNo(String loadNo) { this.loadNo = loadNo; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getBinId() { return binId; }
    public void setBinId(Long binId) { this.binId = binId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getActualAvgWeightG() { return actualAvgWeightG; }
    public void setActualAvgWeightG(BigDecimal actualAvgWeightG) { this.actualAvgWeightG = actualAvgWeightG; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public Long getGradingRuleId() { return gradingRuleId; }
    public void setGradingRuleId(Long gradingRuleId) { this.gradingRuleId = gradingRuleId; }
    public String getLoadedBy() { return loadedBy; }
    public void setLoadedBy(String loadedBy) { this.loadedBy = loadedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
