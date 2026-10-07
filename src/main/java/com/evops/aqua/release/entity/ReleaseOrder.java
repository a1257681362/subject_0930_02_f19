package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 出池检验单：一单一批次，按「品种 + 用途」组合冻结建单时生效的阈值规则版本。
 * 生命周期 DRAFT → SUBMITTED →（退回 RETURNED → 复检 SUBMITTED…）→ SIGNED → SHIPPED。
 * recommendation 为系统按阈值比对生成的放行建议；最终放行以授权签字人电子签名为准。
 */
@TableName("t_rel_order")
public class ReleaseOrder extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_SIGNED = "SIGNED";
    public static final String STATUS_SHIPPED = "SHIPPED";

    /** 放行建议 / 各轮结论取值 */
    public static final String CONCLUSION_PASS = "PASS";
    public static final String CONCLUSION_FAIL = "FAIL";
    public static final String CONCLUSION_PENDING = "PENDING";

    private String orderNo;
    private Long batchId;
    private Long pondId;
    private String speciesCode;
    private String speciesName;
    private String usageCode;
    private String usageName;
    private Long ruleVersionId;
    private String status;
    private Integer currentRoundNo;
    private String recommendation;
    private String recommendationDetail;
    private LocalDateTime submittedTime;
    private LocalDateTime returnedTime;
    private String returnReason;
    private String returnOperator;
    private String signerEmployeeNo;
    private String signerName;
    private String signatureNo;
    private LocalDateTime signedTime;
    private String signatureRemark;
    private Long shipmentId;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public String getSpeciesName() { return speciesName; }
    public void setSpeciesName(String speciesName) { this.speciesName = speciesName; }
    public String getUsageCode() { return usageCode; }
    public void setUsageCode(String usageCode) { this.usageCode = usageCode; }
    public String getUsageName() { return usageName; }
    public void setUsageName(String usageName) { this.usageName = usageName; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentRoundNo() { return currentRoundNo; }
    public void setCurrentRoundNo(Integer currentRoundNo) { this.currentRoundNo = currentRoundNo; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getRecommendationDetail() { return recommendationDetail; }
    public void setRecommendationDetail(String recommendationDetail) { this.recommendationDetail = recommendationDetail; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(LocalDateTime submittedTime) { this.submittedTime = submittedTime; }
    public LocalDateTime getReturnedTime() { return returnedTime; }
    public void setReturnedTime(LocalDateTime returnedTime) { this.returnedTime = returnedTime; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public String getReturnOperator() { return returnOperator; }
    public void setReturnOperator(String returnOperator) { this.returnOperator = returnOperator; }
    public String getSignerEmployeeNo() { return signerEmployeeNo; }
    public void setSignerEmployeeNo(String signerEmployeeNo) { this.signerEmployeeNo = signerEmployeeNo; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public String getSignatureNo() { return signatureNo; }
    public void setSignatureNo(String signatureNo) { this.signatureNo = signatureNo; }
    public LocalDateTime getSignedTime() { return signedTime; }
    public void setSignedTime(LocalDateTime signedTime) { this.signedTime = signedTime; }
    public String getSignatureRemark() { return signatureRemark; }
    public void setSignatureRemark(String signatureRemark) { this.signatureRemark = signatureRemark; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }
}
