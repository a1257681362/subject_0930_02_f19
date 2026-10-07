package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 池塘隔离调度档案：隔离池角色、容量、消毒有效期、调入权限，按池一行。
 * 调度前校验与推荐隔离池均以本档案为准。
 */
@TableName("t_disease_pond_guard")
public class PondGuard extends BaseEntity {

    public static final String ROLE_NORMAL = "NORMAL";
    public static final String ROLE_ISOLATION = "ISOLATION";

    private Long pondId;
    private String pondRole;
    private Integer capacity;
    private LocalDate disinfectedUntil;
    private Boolean transferInAllowed;
    private String operator;
    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getPondRole() { return pondRole; }
    public void setPondRole(String pondRole) { this.pondRole = pondRole; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public LocalDate getDisinfectedUntil() { return disinfectedUntil; }
    public void setDisinfectedUntil(LocalDate disinfectedUntil) { this.disinfectedUntil = disinfectedUntil; }
    public Boolean getTransferInAllowed() { return transferInAllowed; }
    public void setTransferInAllowed(Boolean transferInAllowed) { this.transferInAllowed = transferInAllowed; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
