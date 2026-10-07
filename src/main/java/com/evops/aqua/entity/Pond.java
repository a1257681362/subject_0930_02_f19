package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 养殖池档案：水深、盐度、所属海区。
 * status=DISABLED 表示管理对象置为无效：成本分摊页面/导出/后台报表同口径不再统计，
 * 历史费用凭条与结转快照保留不删。
 * longitude/latitude 为池塘中心点经纬度，供海区禁养多边形（POLYGON）规则做点-多边形空间匹配；
 * 两值为空的池塘只能被整海区（AREA）禁养规则命中。
 */
@TableName("t_pond")
public class Pond extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String pondCode;
    private String pondName;
    private String seaArea;
    private BigDecimal waterDepthM;
    private BigDecimal waterAreaM2;
    private BigDecimal salinityPpt;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String status;
    private String disabledReason;
    private LocalDateTime disabledTime;
    private String remark;

    public String getPondCode() { return pondCode; }
    public void setPondCode(String pondCode) { this.pondCode = pondCode; }
    public String getPondName() { return pondName; }
    public void setPondName(String pondName) { this.pondName = pondName; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public BigDecimal getWaterDepthM() { return waterDepthM; }
    public void setWaterDepthM(BigDecimal waterDepthM) { this.waterDepthM = waterDepthM; }
    public BigDecimal getWaterAreaM2() { return waterAreaM2; }
    public void setWaterAreaM2(BigDecimal waterAreaM2) { this.waterAreaM2 = waterAreaM2; }
    public BigDecimal getSalinityPpt() { return salinityPpt; }
    public void setSalinityPpt(BigDecimal salinityPpt) { this.salinityPpt = salinityPpt; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDisabledReason() { return disabledReason; }
    public void setDisabledReason(String disabledReason) { this.disabledReason = disabledReason; }
    public LocalDateTime getDisabledTime() { return disabledTime; }
    public void setDisabledTime(LocalDateTime disabledTime) { this.disabledTime = disabledTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
