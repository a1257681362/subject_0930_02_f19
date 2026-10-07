package com.evops.aqua.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 建池请求：养殖池档案入口。
 */
public class PondCreateRequest {

    @NotBlank(message = "养殖池编号不能为空")
    @Size(max = 32, message = "养殖池编号最长32位")
    private String pondCode;

    @NotBlank(message = "养殖池名称不能为空")
    @Size(max = 64, message = "养殖池名称最长64位")
    private String pondName;

    @NotBlank(message = "所属海区不能为空")
    @Size(max = 64, message = "所属海区最长64位")
    private String seaArea;

    @NotNull(message = "水深不能为空")
    @DecimalMin(value = "0.1", message = "水深必须大于0")
    private BigDecimal waterDepthM;

    @DecimalMin(value = "0.1", message = "水面面积必须大于0")
    private BigDecimal waterAreaM2;

    @NotNull(message = "盐度不能为空")
    @DecimalMin(value = "0", message = "盐度不能为负")
    @DecimalMax(value = "50", message = "盐度不能超过50‰")
    private BigDecimal salinityPpt;

    /** 池塘中心点经纬度：可空；用于海区禁养多边形（POLYGON）规则的空间匹配。 */
    @DecimalMin(value = "-180", message = "经度取值范围[-180,180]")
    @DecimalMax(value = "180", message = "经度取值范围[-180,180]")
    private BigDecimal longitude;

    @DecimalMin(value = "-90", message = "纬度取值范围[-90,90]")
    @DecimalMax(value = "90", message = "纬度取值范围[-90,90]")
    private BigDecimal latitude;

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
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
