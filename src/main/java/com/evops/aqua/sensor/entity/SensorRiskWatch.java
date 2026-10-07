package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 离线归并高风险观察开关（单行 id=1）。
 * watchEnabled=true 期间针对观察项「传感器补传造成日报重复」实施同步限制：
 * 离线补传归并停止受理（整包拒绝并独立事务留痕，不做任何归并/隔离归档之外的改动），
 * 日报汇总重算同步停止——已审核日报与历史凭证一律冻结，只允许查询、审核与追加留痕。
 */
@TableName("t_sensor_risk_watch")
public class SensorRiskWatch extends BaseEntity {

    public static final Long SINGLETON_ID = 1L;
    public static final String SUBJECT_RETRANSMIT_DUPLICATE = "传感器补传造成日报重复";

    private String watchSubject;
    private Boolean watchEnabled;
    private String changedReason;

    public String getWatchSubject() { return watchSubject; }
    public void setWatchSubject(String watchSubject) { this.watchSubject = watchSubject; }
    public Boolean getWatchEnabled() { return watchEnabled; }
    public void setWatchEnabled(Boolean watchEnabled) { this.watchEnabled = watchEnabled; }
    public String getChangedReason() { return changedReason; }
    public void setChangedReason(String changedReason) { this.changedReason = changedReason; }
}
