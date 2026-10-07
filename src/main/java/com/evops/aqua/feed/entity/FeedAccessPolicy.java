package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 海水养殖管理权限开关（单行 id=1）。
 * feedBatchAdvanceEnabled=false 表示权限收紧：饲料批号停止推进
 * （禁止新批号入库与投喂消耗），已确认的配方版本与历史投喂事实一律保留不变。
 */
@TableName("t_feed_access_policy")
public class FeedAccessPolicy extends BaseEntity {

    public static final Long SINGLETON_ID = 1L;

    private Boolean feedBatchAdvanceEnabled;
    private String changedReason;

    public Boolean getFeedBatchAdvanceEnabled() { return feedBatchAdvanceEnabled; }
    public void setFeedBatchAdvanceEnabled(Boolean feedBatchAdvanceEnabled) {
        this.feedBatchAdvanceEnabled = feedBatchAdvanceEnabled;
    }
    public String getChangedReason() { return changedReason; }
    public void setChangedReason(String changedReason) { this.changedReason = changedReason; }
}
