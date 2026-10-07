package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 派工请求：工单关联至少一名维修人员，派工人必填。
 * 人员资质须覆盖设备类型，缺资质或人员停用在业务层拒绝。
 */
public class AssignWorkersRequest {

    @NotEmpty(message = "至少派一名维修人员")
    private List<Long> workerIds;
    @NotBlank(message = "派工人不能为空")
    private String assignedBy;

    public List<Long> getWorkerIds() { return workerIds; }
    public void setWorkerIds(List<Long> workerIds) { this.workerIds = workerIds; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
}
