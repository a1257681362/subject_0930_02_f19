package com.evops.aqua.sensor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备离线批量上传报文：一批多条采样记录，每条带设备时间与序号，
 * 盐度/溶氧/温度可只带部分指标（缺项该指标不归并）。
 * 报文以原始字符串接收验签，验签通过后才解析为本结构。
 */
public class UploadPayload {

    private List<UploadReadingItem> readings;

    public List<UploadReadingItem> getReadings() { return readings; }
    public void setReadings(List<UploadReadingItem> readings) { this.readings = readings; }

    public static class UploadReadingItem {
        private LocalDateTime deviceTime;
        private Integer seqNo;
        private BigDecimal salinity;
        private BigDecimal dissolvedOxygen;
        private BigDecimal temperature;

        public LocalDateTime getDeviceTime() { return deviceTime; }
        public void setDeviceTime(LocalDateTime deviceTime) { this.deviceTime = deviceTime; }
        public Integer getSeqNo() { return seqNo; }
        public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
        public BigDecimal getSalinity() { return salinity; }
        public void setSalinity(BigDecimal salinity) { this.salinity = salinity; }
        public BigDecimal getDissolvedOxygen() { return dissolvedOxygen; }
        public void setDissolvedOxygen(BigDecimal dissolvedOxygen) { this.dissolvedOxygen = dissolvedOxygen; }
        public BigDecimal getTemperature() { return temperature; }
        public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    }
}
