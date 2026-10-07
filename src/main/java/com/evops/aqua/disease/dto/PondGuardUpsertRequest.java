package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * 池塘隔离调度档案登记/更新请求：隔离池角色、容量、消毒有效期、调入权限。
 * 操作者必填——档案谁登记谁负责，不丢操作者。
 */
public class PondGuardUpsertRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    /** NORMAL=普通池，ISOLATION=隔离池；为空按普通池。 */
    private String pondRole;

    /** 隔离池容量（尾数）；普通池可不填。 */
    @Positive(message = "容量必须为正数")
    private Integer capacity;

    /** 消毒有效期至（含当日）；未消毒可空。 */
    private LocalDate disinfectedUntil;

    /** 目标池权限：是否允许调入；为空按允许。 */
    private Boolean transferInAllowed;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    private String remark;

    private String requestId;

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
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
