package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 批量冻结明细：逐人记录冻结结果（FROZEN / ALREADY_FROZEN / NOT_FOUND）
 * 与冻结时刻的影响快照（受影响工单 / 未完成任务 JSON），留档可复核。
 */
@TableName("t_qual_freeze_line")
public class FreezeLine extends BaseEntity {

    public static final String RESULT_FROZEN = "FROZEN";
    public static final String RESULT_ALREADY_FROZEN = "ALREADY_FROZEN";
    public static final String RESULT_NOT_FOUND = "NOT_FOUND";

    private Long batchId;
    private Long personId;
    private String personCode;
    private String personName;
    private String result;
    private String affectedOrdersJson;
    private String unfinishedTasksJson;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
    public String getPersonCode() { return personCode; }
    public void setPersonCode(String personCode) { this.personCode = personCode; }
    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getAffectedOrdersJson() { return affectedOrdersJson; }
    public void setAffectedOrdersJson(String affectedOrdersJson) { this.affectedOrdersJson = affectedOrdersJson; }
    public String getUnfinishedTasksJson() { return unfinishedTasksJson; }
    public void setUnfinishedTasksJson(String unfinishedTasksJson) { this.unfinishedTasksJson = unfinishedTasksJson; }
}
