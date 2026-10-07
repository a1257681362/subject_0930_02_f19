package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 养殖阶段规则：阶段编码 + 采样频率（间隔天数），属于某个规则版本。
 */
@TableName("t_wq_stage_rule")
public class WqStageRule extends BaseEntity {

    private Long versionId;
    private String stageCode;
    private String stageName;
    private Integer sampleIntervalDays;
    private Integer sortNo;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getStageCode() { return stageCode; }
    public void setStageCode(String stageCode) { this.stageCode = stageCode; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public Integer getSampleIntervalDays() { return sampleIntervalDays; }
    public void setSampleIntervalDays(Integer sampleIntervalDays) { this.sampleIntervalDays = sampleIntervalDays; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
