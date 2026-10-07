package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 水质指标规则版本。同一时刻仅一个 ACTIVE 版本；
 * 发布新版本时旧版本置为 SUPERSEDED，已开始任务仍冻结使用旧版本。
 */
@TableName("t_wq_rule_version")
public class WqRuleVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String versionNo;
    private String status;
    private String remark;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
