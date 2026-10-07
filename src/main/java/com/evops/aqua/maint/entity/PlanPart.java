package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 计划备件模板：计划挂备件品类与数量，生成工单时复制为工单备件需求行（快照）。
 */
@TableName("t_maint_plan_part")
public class PlanPart extends BaseEntity {

    private Long planId;
    private Long catalogId;
    private Integer quantity;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
