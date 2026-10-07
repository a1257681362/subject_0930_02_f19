package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 用药记录：按池按批留痕。
 */
@TableName("t_medication_record")
public class MedicationRecord extends BaseEntity {

    private Long pondId;
    private Long batchId;
    private LocalDate useDate;
    private String medicineName;
    private String dosage;
    private String reason;
    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getUseDate() { return useDate; }
    public void setUseDate(LocalDate useDate) { this.useDate = useDate; }
    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
