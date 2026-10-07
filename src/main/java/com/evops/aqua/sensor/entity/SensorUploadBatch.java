package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 上传批次（包）：request_id 整包幂等，重复上传返回首次结果。
 * 保留原始报文与签名；签名失败整包置 QUARANTINED，不进入归并。
 */
@TableName("t_sensor_upload_batch")
public class SensorUploadBatch extends BaseEntity {

    public static final String STATUS_MERGED = "MERGED";
    public static final String STATUS_QUARANTINED = "QUARANTINED";

    private String requestId;
    private Long deviceId;
    private String deviceCode;
    private String signature;
    private Boolean signatureOk;
    private String status;
    private Integer totalCount;
    private Integer mergedCount;
    private Integer duplicateCount;
    private Integer lateRejectedCount;
    private Integer abnormalCount;
    private Long ruleVersionId;
    private String rawPayload;
    private String rejectReason;
    private LocalDateTime receivedTime;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public Boolean getSignatureOk() { return signatureOk; }
    public void setSignatureOk(Boolean signatureOk) { this.signatureOk = signatureOk; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getMergedCount() { return mergedCount; }
    public void setMergedCount(Integer mergedCount) { this.mergedCount = mergedCount; }
    public Integer getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(Integer duplicateCount) { this.duplicateCount = duplicateCount; }
    public Integer getLateRejectedCount() { return lateRejectedCount; }
    public void setLateRejectedCount(Integer lateRejectedCount) { this.lateRejectedCount = lateRejectedCount; }
    public Integer getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(Integer abnormalCount) { this.abnormalCount = abnormalCount; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getReceivedTime() { return receivedTime; }
    public void setReceivedTime(LocalDateTime receivedTime) { this.receivedTime = receivedTime; }
}
