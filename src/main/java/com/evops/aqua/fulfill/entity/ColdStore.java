package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 冷藏库档案：在库重量账随箱入库累加、正常箱交接出库扣减；
 * 超差冻结箱重量永久占用容量，不做出库扣减。
 */
@TableName("t_fulfill_cold_store")
public class ColdStore extends BaseEntity {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private String storeNo;
    private String storeName;
    private BigDecimal capacityKg;
    private BigDecimal storedKg;
    private String status;
    private String operator;
    private String requestId;
    private String remark;

    public String getStoreNo() { return storeNo; }
    public void setStoreNo(String storeNo) { this.storeNo = storeNo; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public BigDecimal getStoredKg() { return storedKg; }
    public void setStoredKg(BigDecimal storedKg) { this.storedKg = storedKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
