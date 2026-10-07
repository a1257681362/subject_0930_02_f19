package com.evops.aqua.fulfill.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 交接请求：一次提交该子单全部在库箱的交接净重。
 * 系统按当时生效净重差异规则逐箱判定：超差箱冻结在库，正常箱出库；requestId 幂等。
 */
public class HandoverCreateRequest {

    @NotEmpty(message = "交接箱清单不能为空")
    @Valid
    private List<HandoverBox> boxes;

    @NotBlank(message = "交接人不能为空")
    private String handoverBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public List<HandoverBox> getBoxes() { return boxes; }
    public void setBoxes(List<HandoverBox> boxes) { this.boxes = boxes; }
    public String getHandoverBy() { return handoverBy; }
    public void setHandoverBy(String handoverBy) { this.handoverBy = handoverBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public static class HandoverBox {

        @NotNull(message = "箱id不能为空")
        private Long boxId;

        @NotNull(message = "交接净重不能为空")
        private BigDecimal handoverNetKg;

        public Long getBoxId() { return boxId; }
        public void setBoxId(Long boxId) { this.boxId = boxId; }
        public BigDecimal getHandoverNetKg() { return handoverNetKg; }
        public void setHandoverNetKg(BigDecimal handoverNetKg) { this.handoverNetKg = handoverNetKg; }
    }
}
