package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 定时复查任务：调度系统按观察单按日生成。
 * task_no = FUP-{观察单id}-{yyyyMMdd} 唯一——定时任务重启或重跑都不会多写第二条。
 * operator 固定为调度系统标识，source_observer 保留原观察人，操作者链条不断。
 */
@TableName("t_disease_followup_task")
public class DiseaseFollowupTask extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";

    /** 定时任务生成记录时的操作者标识。 */
    public static final String SCHEDULER_OPERATOR = "system-scheduler";

    private String taskNo;
    private Long observationId;
    private LocalDate taskDate;
    private String status;
    private String operator;
    private String sourceObserver;
    private String remark;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public LocalDate getTaskDate() { return taskDate; }
    public void setTaskDate(LocalDate taskDate) { this.taskDate = taskDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getSourceObserver() { return sourceObserver; }
    public void setSourceObserver(String sourceObserver) { this.sourceObserver = sourceObserver; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
