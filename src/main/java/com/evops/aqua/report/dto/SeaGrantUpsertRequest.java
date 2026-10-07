package com.evops.aqua.report.dto;

import javax.validation.constraints.NotBlank;

/**
 * 用户海区数据权限登记：seaAreas 为逗号分隔海区名，"*" 表示全部海区。
 */
public class SeaGrantUpsertRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "授权海区不能为空（* 表示全部海区）")
    private String seaAreas;
    private String remark;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSeaAreas() { return seaAreas; }
    public void setSeaAreas(String seaAreas) { this.seaAreas = seaAreas; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
