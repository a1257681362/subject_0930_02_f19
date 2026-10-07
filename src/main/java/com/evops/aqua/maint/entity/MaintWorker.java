package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 维修人员档案：派工对象，资质按设备类型登记（逗号分隔 AERATOR,PUMP_STATION,FEEDER）。
 */
@TableName("t_maint_worker")
public class MaintWorker extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private String workerCode;
    private String workerName;
    private String qualifiedTypes;
    private String phone;
    private String status;

    public String getWorkerCode() { return workerCode; }
    public void setWorkerCode(String workerCode) { this.workerCode = workerCode; }
    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }
    public String getQualifiedTypes() { return qualifiedTypes; }
    public void setQualifiedTypes(String qualifiedTypes) { this.qualifiedTypes = qualifiedTypes; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
