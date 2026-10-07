package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 疫病观察单：发现异常症状即生成，记录症状、风险等级与观察人。
 * requestId 强制幂等：重复提交返回同一单，不产生第二条。
 * 状态 OBSERVING（观察中）/ ISOLATED（已隔离）/ RELEASED（已解除）。
 */
@TableName("t_disease_observation")
public class DiseaseObservation extends BaseEntity {

    public static final String STATUS_OBSERVING = "OBSERVING";
    public static final String STATUS_ISOLATED = "ISOLATED";
    public static final String STATUS_RELEASED = "RELEASED";

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    private String obsNo;
    private Long pondId;
    private Long batchId;
    private LocalDate observeDate;
    private String symptoms;
    private String riskLevel;
    private String status;
    private String observer;
    private String requestId;
    private String remark;

    public String getObsNo() { return obsNo; }
    public void setObsNo(String obsNo) { this.obsNo = obsNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getObserveDate() { return observeDate; }
    public void setObserveDate(LocalDate observeDate) { this.observeDate = observeDate; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getObserver() { return observer; }
    public void setObserver(String observer) { this.observer = observer; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
