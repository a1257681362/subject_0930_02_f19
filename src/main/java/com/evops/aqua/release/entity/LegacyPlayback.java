package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 旧数据回放记录：只读回放海水养殖台账（aqua-1）数据并生成差异解释，绝不修改旧数据。
 * requestId 幂等：重复回放请求返回首次结果，不产生第二条记录。
 */
@TableName("t_rel_playback")
public class LegacyPlayback extends BaseEntity {

    private String requestId;
    private String sourceVersion;
    private Integer pondCount;
    private Integer batchCount;
    private Integer stockedCount;
    private Integer harvestedCount;
    private Integer inspectionCount;
    private String findingsJson;
    private String diffSummary;
    private String operator;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public Integer getPondCount() { return pondCount; }
    public void setPondCount(Integer pondCount) { this.pondCount = pondCount; }
    public Integer getBatchCount() { return batchCount; }
    public void setBatchCount(Integer batchCount) { this.batchCount = batchCount; }
    public Integer getStockedCount() { return stockedCount; }
    public void setStockedCount(Integer stockedCount) { this.stockedCount = stockedCount; }
    public Integer getHarvestedCount() { return harvestedCount; }
    public void setHarvestedCount(Integer harvestedCount) { this.harvestedCount = harvestedCount; }
    public Integer getInspectionCount() { return inspectionCount; }
    public void setInspectionCount(Integer inspectionCount) { this.inspectionCount = inspectionCount; }
    public String getFindingsJson() { return findingsJson; }
    public void setFindingsJson(String findingsJson) { this.findingsJson = findingsJson; }
    public String getDiffSummary() { return diffSummary; }
    public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
