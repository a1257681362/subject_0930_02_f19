package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 冷藏库登记请求；requestId 幂等。
 */
public class ColdStoreRegisterRequest {

    @NotBlank(message = "冷藏库编号不能为空")
    private String storeNo;

    @NotBlank(message = "冷藏库名称不能为空")
    private String storeName;

    @NotNull(message = "容量不能为空")
    private BigDecimal capacityKg;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getStoreNo() { return storeNo; }
    public void setStoreNo(String storeNo) { this.storeNo = storeNo; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
