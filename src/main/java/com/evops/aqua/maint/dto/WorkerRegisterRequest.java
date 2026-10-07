package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 维修人员登记请求：资质类型逗号分隔（AERATOR,PUMP_STATION,FEEDER）。
 */
public class WorkerRegisterRequest {

    @NotBlank(message = "人员工号不能为空")
    private String workerCode;
    @NotBlank(message = "人员姓名不能为空")
    private String workerName;
    private String qualifiedTypes;
    private String phone;

    public String getWorkerCode() { return workerCode; }
    public void setWorkerCode(String workerCode) { this.workerCode = workerCode; }
    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }
    public String getQualifiedTypes() { return qualifiedTypes; }
    public void setQualifiedTypes(String qualifiedTypes) { this.qualifiedTypes = qualifiedTypes; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
