package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 出池检验阈值规则版本：检验项目按「品种 + 用途」组合配置。
 * 同一时刻仅一个 ACTIVE，发布新版本后旧版本置 SUPERSEDED；
 * 已建立的检验单冻结建单时版本，历史检验结论不受新版本影响。
 */
@TableName("t_rel_rule_version")
public class ReleaseRuleVersion extends BaseEntity {

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
