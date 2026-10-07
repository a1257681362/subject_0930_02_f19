package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 投喂配方版本：同一苗种同一时刻仅一个 ACTIVE 版本。
 * 配方调整产生新版本，旧版本置 SUPERSEDED；历史投喂单继续引用其冻结的旧版本快照。
 */
@TableName("t_feed_formula_version")
public class FeedFormulaVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String versionNo;
    private String species;
    private String status;
    private String remark;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
