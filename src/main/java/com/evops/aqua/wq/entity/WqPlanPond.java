package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 计划覆盖的池塘。
 */
@TableName("t_wq_plan_pond")
public class WqPlanPond extends BaseEntity {

    private Long planId;
    private Long pondId;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
}
