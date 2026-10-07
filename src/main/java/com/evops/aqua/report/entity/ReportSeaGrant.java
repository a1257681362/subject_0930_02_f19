package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 用户海区数据权限：username → 授权海区逗号分隔（* 表示全部海区）。
 * 月报查询与导出共用同一授权口径，越海区数据不下发。
 */
@TableName("t_report_sea_grant")
public class ReportSeaGrant extends BaseEntity {

    public static final String ALL_AREAS = "*";

    private String username;
    private String seaAreas;
    private String remark;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSeaAreas() { return seaAreas; }
    public void setSeaAreas(String seaAreas) { this.seaAreas = seaAreas; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
