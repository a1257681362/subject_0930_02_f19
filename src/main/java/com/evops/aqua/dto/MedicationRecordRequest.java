package com.evops.aqua.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 用药请求：按池按批留痕。
 */
public class MedicationRecordRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "用药日期不能为空")
    private LocalDate useDate;

    @NotBlank(message = "药品名称不能为空")
    private String medicineName;

    @NotBlank(message = "剂量不能为空")
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
