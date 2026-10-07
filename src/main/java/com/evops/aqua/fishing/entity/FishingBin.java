package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位（分仓）：一仓对应一个规格。
 * 状态 EMPTY（空闲）/ LOADING（装货中，待检）/ FULL（达容量，待检）/ CLEARED（检验完成），
 * 与待检标识 pendingInspection 同一事务同步更新，不出现中间态。
 * occupiedOrderId/occupiedResvId 记录当前占用订单/预约：预约锁定后他单不得占用。
 * 可空字段（占用方、检验人）采用 ALWAYS 更新策略——释放/复位时必须能把旧值置空落库。
 */
@TableName("t_fishing_bin")
public class FishingBin extends BaseEntity {

    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_FULL = "FULL";
    public static final String STATUS_CLEARED = "CLEARED";

    private String binNo;
    private String specCode;
    private BigDecimal capacityKg;
    private BigDecimal loadedKg;
    private String status;
    private Boolean pendingInspection;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long occupiedOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long occupiedResvId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String inspectedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime inspectedTime;
    private String operator;
    private String requestId;
    private String remark;

    public String getBinNo() { return binNo; }
    public void setBinNo(String binNo) { this.binNo = binNo; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public BigDecimal getLoadedKg() { return loadedKg; }
    public void setLoadedKg(BigDecimal loadedKg) { this.loadedKg = loadedKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getPendingInspection() { return pendingInspection; }
    public void setPendingInspection(Boolean pendingInspection) { this.pendingInspection = pendingInspection; }
    public Long getOccupiedOrderId() { return occupiedOrderId; }
    public void setOccupiedOrderId(Long occupiedOrderId) { this.occupiedOrderId = occupiedOrderId; }
    public Long getOccupiedResvId() { return occupiedResvId; }
    public void setOccupiedResvId(Long occupiedResvId) { this.occupiedResvId = occupiedResvId; }
    public String getInspectedBy() { return inspectedBy; }
    public void setInspectedBy(String inspectedBy) { this.inspectedBy = inspectedBy; }
    public LocalDateTime getInspectedTime() { return inspectedTime; }
    public void setInspectedTime(LocalDateTime inspectedTime) { this.inspectedTime = inspectedTime; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
