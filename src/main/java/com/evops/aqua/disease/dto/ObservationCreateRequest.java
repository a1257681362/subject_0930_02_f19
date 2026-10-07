package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 疫病观察单创建请求：发现异常症状即登记。
 * requestId 强制幂等——重复提交返回首次生成的观察单；观察人必填，不丢操作者。
 */
public class ObservationCreateRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "观察日期不能为空")
    private LocalDate observeDate;

    @NotBlank(message = "异常症状描述不能为空")
    private String symptoms;

    /** LOW / MEDIUM / HIGH，为空按 LOW。 */
    private String riskLevel;

    @NotBlank(message = "观察人不能为空")
    private String observer;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    private String remark;

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
    public String getObserver() { return observer; }
    public void setObserver(String observer) { this.observer = observer; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
