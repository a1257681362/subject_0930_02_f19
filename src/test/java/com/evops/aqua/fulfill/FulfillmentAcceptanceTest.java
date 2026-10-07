package com.evops.aqua.fulfill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.FishingOrderMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.evops.aqua.fulfill.entity.ColdStore;
import com.evops.aqua.fulfill.entity.FulfillBox;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.FulfillDelivery;
import com.evops.aqua.fulfill.entity.FulfillHandover;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.entity.FulfillOutbox;
import com.evops.aqua.fulfill.mapper.ColdStoreMapper;
import com.evops.aqua.fulfill.mapper.FulfillBoxMapper;
import com.evops.aqua.fulfill.mapper.FulfillChangeEvidenceMapper;
import com.evops.aqua.fulfill.mapper.FulfillDeliveryMapper;
import com.evops.aqua.fulfill.mapper.FulfillHandoverMapper;
import com.evops.aqua.fulfill.mapper.FulfillOrderMapper;
import com.evops.aqua.fulfill.mapper.FulfillOutboxMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 渔获订单履约验收（aqua-14）。
 * 主线：收货点/冷库/差异规则主数据 → 按规格×收货点拆履约单（金额口径守恒）→ 再拆/改点审批
 * → 捕捞后入冷藏库（温度/箱号/称重）→ 交接逐箱判差异、超差冻结 → 客户首签 → 订单完成；
 * 外部海水养殖管理无响应时拆单不假装成功，恢复后发件箱重发且不重复通知；客户视图不见内部成本。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FulfillmentAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FishingOrderMapper fishingOrderMapper;
    @Autowired
    private FishingReservationMapper fishingReservationMapper;
    @Autowired
    private FulfillOrderMapper fulfillOrderMapper;
    @Autowired
    private FulfillDeliveryMapper deliveryMapper;
    @Autowired
    private FulfillBoxMapper boxMapper;
    @Autowired
    private ColdStoreMapper coldStoreMapper;
    @Autowired
    private FulfillHandoverMapper handoverMapper;
    @Autowired
    private FulfillOutboxMapper outboxMapper;
    @Autowired
    private FulfillChangeEvidenceMapper evidenceMapper;
    @Autowired
    private Scheduler quartzScheduler;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long foAId;
    private Long foBId;
    private Long ra1;
    private Long ra2;
    private Long rb1;
    private Long pointA;
    private Long pointB;
    private Long store1;
    private Long ruleV1;
    private Long ruleV2;
    private Long orderA;
    private Long orderB;
    private Long dL60;
    private Long dL40;
    private Long dM80;
    private Long dChild1;
    private Long dChild2;
    private Long b1;
    private Long b2;
    private Long b3;
    private Long b4;
    private Long b5;
    private Long b6;
    private Long bb1;
    private Long handoverM;

    @Test
    void acceptanceFulfillment() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "收货点主数据：登记/幂等/编号唯一", () -> {
            JsonNode a = json(call(HttpMethod.POST, "/api/aqua/fulfill/points",
                    pointBody("DP-A", "上海第一收货点", "point-a")));
            assertSuccess(a, "登记收货点A");
            pointA = a.path("data").path("id").asLong();
            JsonNode b = json(call(HttpMethod.POST, "/api/aqua/fulfill/points",
                    pointBody("DP-B", "宁波第二收货点", "point-b")));
            assertSuccess(b, "登记收货点B");
            pointB = b.path("data").path("id").asLong();

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fulfill/points",
                    pointBody("DP-A", "上海第一收货点", "point-a")));
            assertSuccess(again, "同requestId重复登记收货点");
            check(again.path("data").path("id").asLong() == pointA, "重复登记应返回同一收货点");

            JsonNode dupNo = json(call(HttpMethod.POST, "/api/aqua/fulfill/points",
                    pointBody("DP-A", "重复编号", "point-a-dup")));
            check(!dupNo.path("success").asBoolean(true), "异requestId同编号应被拒绝");
            check(dupNo.path("message").asText().contains("编号已存在"), "拒绝原因应为编号已存在");
        });

        checkpoint("CP2", "净重差异规则版本V1：L/M容差、版本化、幂等与重复规格拒绝", () -> {
            JsonNode v1 = json(call(HttpMethod.POST, "/api/aqua/fulfill/variance-rules",
                    ruleBody("vr-v1", vrLine("L", "2", "1"), vrLine("M", "5", "0.5"))));
            assertSuccess(v1, "发布差异规则V1");
            ruleV1 = v1.path("data").path("id").asLong();
            check("FVR-V1".equals(v1.path("data").path("ruleNo").asText()), "规则号应为FVR-V1");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fulfill/variance-rules",
                    ruleBody("vr-v1", vrLine("L", "2", "1"))));
            assertSuccess(again, "同requestId重复发布规则");
            check(again.path("data").path("id").asLong() == ruleV1, "重复发布应返回同一版本");

            JsonNode dupSpec = json(call(HttpMethod.POST, "/api/aqua/fulfill/variance-rules",
                    ruleBody("vr-v1-bad", vrLine("L", "1", "0"), vrLine("L", "2", "0"))));
            check(!dupSpec.path("success").asBoolean(true), "同版本内重复规格应拒绝");
            check(dupSpec.path("message").asText().contains("重复"), "应提示规格重复");
        });

        checkpoint("CP3", "冷藏库登记：容量500kg，编号唯一", () -> {
            JsonNode s = json(call(HttpMethod.POST, "/api/aqua/fulfill/cold-stores",
                    storeBody("CS-1", "一号冷藏库", "500", "store-1")));
            assertSuccess(s, "登记冷藏库");
            store1 = s.path("data").path("id").asLong();
            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/fulfill/cold-stores",
                    storeBody("CS-1", "重复冷库", "500", "store-1-dup")));
            check(!dup.path("success").asBoolean(true), "同编号冷库应拒绝");
        });

        seedFishingOrders();

        checkpoint("CP4", "建履约单：按规格×收货点拆3行、总额2600、即时同步OPEN；非法来源/超量拦截", () -> {
            JsonNode notExist = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(999999L, "ff-bad-src",
                            fulfillLine(ra1, "L", pointA, "10", "10"))));
            check(!notExist.path("success").asBoolean(true), "不存在的捕捞订单应拒绝");

            JsonNode notCompleted = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(foCId, "ff-bad-status",
                            fulfillLine(rc1, "L", pointA, "10", "10"))));
            check(!notCompleted.path("success").asBoolean(true), "未完成捕捞订单应拒绝");
            check(notCompleted.path("message").asText().contains("尚未完成"), "应提示来源订单尚未完成");

            JsonNode overQty = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(foAId, "ff-bad-qty",
                            fulfillLine(ra1, "L", pointA, "101", "10"))));
            check(!overQty.path("success").asBoolean(true), "计划净重超实际捕捞量应拒绝");
            check(overQty.path("message").asText().contains("实际捕捞量"), "应提示超过实际捕捞量");

            JsonNode ok = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(foAId, "ff-order-a",
                            fulfillLine(ra1, "L", pointA, "60", "10"),
                            fulfillLine(ra1, "L", pointB, "40", "10"),
                            fulfillLine(ra2, "M", pointA, "80", "20"))));
            assertSuccess(ok, "创建履约订单A");
            orderA = ok.path("data").path("id").asLong();
            check("OPEN".equals(ok.path("data").path("status").asText()), "网关UP时建单应即时确认OPEN");
            check(Math.abs(ok.path("data").path("totalAmount").asDouble() - 2600.00) < 1e-6,
                    "订单总额应=60*10+40*10+80*20=2600");
            check(deliveryMapper.selectCount(new QueryWrapper<FulfillDelivery>()
                    .eq("order_id", orderA)) == 3, "应拆出3条子单");

            FulfillOutbox ob = outboxMapper.selectOne(new QueryWrapper<FulfillOutbox>()
                    .eq("biz_id", orderA).last("limit 1"));
            check(FulfillOutbox.STATUS_SENT.equals(ob.getStatus()), "发件箱应已SENT");
            check("FULFILL_SPLIT#ff-order-a".equals(ob.getEventId()), "eventId应确定性派生");
            check(!ob.getPayload().contains("unitPrice") && !ob.getPayload().contains("2600"),
                    "外发载荷不得含单价/金额等内部口径");
            JsonNode sim = json(call(HttpMethod.GET, "/api/aqua/fulfill/gateway/sim", null));
            check(sim.path("data").path("receivedCount").asInt() == 1, "模拟对端应只收到1个事件");
        });

        checkpoint("CP5", "拆单不改变金额口径：子单行金额合计恒等于订单总额", () -> {
            FulfillOrder order = fulfillOrderMapper.selectById(orderA);
            BigDecimal sum = BigDecimal.ZERO;
            for (FulfillDelivery d : deliveryMapper.selectList(new QueryWrapper<FulfillDelivery>()
                    .eq("order_id", orderA))) {
                check(d.getLineAmount().compareTo(d.getPlannedNetKg().multiply(d.getUnitPrice())) == 0,
                        "行金额必须=数量×单价");
                sum = sum.add(d.getLineAmount());
            }
            check(sum.compareTo(order.getTotalAmount()) == 0, "Σ行金额必须等于订单总额2600");
        });

        checkpoint("CP6", "子单再拆：数量切分+比例分摊尾差归原行、单价不变、口径守恒、重放不多拆", () -> {
            List<FulfillDelivery> before = deliveriesOfOrder(orderA);
            dL60 = before.get(0).getId();
            dL40 = before.get(1).getId();
            dM80 = before.get(2).getId();
            check(before.get(0).getPlannedNetKg().compareTo(new BigDecimal("60")) == 0, "首行应为L60");

            JsonNode split = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/split",
                    splitBody("split-1", "15", "15")));
            assertSuccess(split, "再拆L60为15+15（原行留30）");

            FulfillDelivery parent = deliveryMapper.selectById(dL60);
            check(parent.getPlannedNetKg().compareTo(new BigDecimal("30")) == 0, "原行数量应留尾差30");
            check(parent.getLineAmount().compareTo(new BigDecimal("300.00")) == 0, "原行金额应为300（尾差归原行）");
            check(parent.getUnitPrice().compareTo(new BigDecimal("10")) == 0, "单价必须保持10不变");
            List<FulfillDelivery> children = deliveryMapper.selectList(new QueryWrapper<FulfillDelivery>()
                    .eq("parent_id", dL60).orderByAsc("id"));
            check(children.size() == 2, "应产生2条子行");
            dChild1 = children.get(0).getId();
            dChild2 = children.get(1).getId();
            check(children.get(0).getLineAmount().compareTo(new BigDecimal("150.00")) == 0
                    && children.get(1).getLineAmount().compareTo(new BigDecimal("150.00")) == 0,
                    "两条子行金额应各150");
            check("split-1#c1".equals(children.get(0).getRequestId()), "子行requestId应派生#c1");
            assertAmountConserved(orderA);

            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/split",
                    splitBody("split-1", "15", "15")));
            assertSuccess(replay, "同requestId重放再拆");
            check(deliveryMapper.selectCount(new QueryWrapper<FulfillDelivery>()
                    .eq("parent_id", dL60)) == 2, "重放不得多拆子行");

            JsonNode badSum = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/split",
                    splitBody("split-bad", "20", "10")));
            check(!badSum.path("success").asBoolean(true), "子行合计≥原行数量应拒绝");
        });

        checkpoint("CP7", "替换收货点需审批：缺审批人拒绝、重复PENDING拒绝、驳回不改点、通过只改点不碰金额", () -> {
            // L40 行申请改到A点
            JsonNode submit = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL40 + "/point-changes",
                    pointChangeSubmitBody(pointA, "客户临时改址", "调度员", "pc-1")));
            assertSuccess(submit, "提交改点申请");
            Long pc1 = submit.path("data").path("id").asLong();
            check("PENDING".equals(submit.path("data").path("status").asText()), "申请应停留PENDING");

            JsonNode noReason = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/point-changes",
                    pointChangeSubmitBody(pointB, null, "调度员", "pc-noreason")));
            check(!noReason.path("success").asBoolean(true), "缺原因应参数校验拒绝");

            JsonNode secondPending = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL40 + "/point-changes",
                    pointChangeSubmitBody(pointA, "又一条", "调度员", "pc-2")));
            check(!secondPending.path("success").asBoolean(true), "同一子单第二条在途申请应拒绝");

            JsonNode missingApprover = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/point-changes/" + pc1 + "/approve",
                    reviewBody(null, "同意", "pc-approve-noperson")));
            check(!missingApprover.path("success").asBoolean(true), "缺审批人应拒绝");

            // M80 行申请改B点后驳回，收货点保持A
            JsonNode submit2 = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/point-changes",
                    pointChangeSubmitBody(pointB, "也想改", "调度员", "pc-3")));
            assertSuccess(submit2, "提交第二条改点申请");
            Long pc3 = submit2.path("data").path("id").asLong();
            JsonNode reject = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/point-changes/" + pc3 + "/reject",
                    reviewBody("主管李", "驳回，维持原收货点", "pc-3-reject")));
            assertSuccess(reject, "驳回第二条申请");
            check("REJECTED".equals(reject.path("data").path("status").asText()), "状态应为REJECTED");
            check(deliveryMapper.selectById(dM80).getDeliveryPointId().equals(pointA), "驳回后收货点应不变");

            JsonNode approve = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/point-changes/" + pc1 + "/approve",
                    reviewBody("主管李", "同意改到上海点", "pc-1-approve")));
            assertSuccess(approve, "审批通过第一条申请");
            FulfillDelivery changed = deliveryMapper.selectById(dL40);
            check(changed.getDeliveryPointId().equals(pointA), "通过后子单应指向A点");
            check(changed.getPlannedNetKg().compareTo(new BigDecimal("40")) == 0
                    && changed.getLineAmount().compareTo(new BigDecimal("400.00")) == 0,
                    "改点不得改变数量与金额");
            assertAmountConserved(orderA);

            JsonNode approveReplay = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/point-changes/" + pc1 + "/approve",
                    reviewBody("主管李", "重复审批", "pc-1-approve")));
            assertSuccess(approveReplay, "重复审批应幂等");
        });

        checkpoint("CP8", "捕捞后入冷藏库：温度/箱号/毛皮称重三要素，净重服务端算，拦截与幂等不重复加账", () -> {
            b1 = intakeBox(dL60, "B1", store1, "2.0", "32", "2", "box-1");
            FulfillBox box1 = boxMapper.selectById(b1);
            check(box1.getNetKg().compareTo(new BigDecimal("30.00")) == 0, "净重应由服务端=毛32-皮2=30");
            check(coldStoreMapper.selectById(store1).getStoredKg().compareTo(new BigDecimal("30.00")) == 0,
                    "冷库在库重量应累加到30");

            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/boxes",
                    boxBody("B1", store1, "2.0", "32", "2", "box-1")));
            assertSuccess(replay, "同requestId重放入库");
            check(replay.path("data").path("id").asLong() == b1, "重放应返回首箱");
            check(coldStoreMapper.selectById(store1).getStoredKg().compareTo(new BigDecimal("30.00")) == 0,
                    "重放不得重复累加冷库重量");

            JsonNode badWeight = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/boxes",
                    boxBody("B-BAD", store1, "2.0", "10", "20", "box-bad")));
            check(!badWeight.path("success").asBoolean(true), "毛重≤皮重应拒绝");
            check(badWeight.path("message").asText().contains("毛重"), "应提示毛重必须大于皮重");

            JsonNode dupBoxNo = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/boxes",
                    boxBody("B1", store1, "3.0", "33", "3", "box-dupno")));
            check(!dupBoxNo.path("success").asBoolean(true), "异requestId同箱号应拒绝");

            b2 = intakeBox(dChild1, "B2", store1, "2.1", "16", "1", "box-2");
            b3 = intakeBox(dChild2, "B3", store1, "2.2", "17", "2", "box-3");
            b4 = intakeBox(dL40, "B4", store1, "2.3", "42", "2", "box-4");
            b5 = intakeBox(dM80, "B5", store1, "2.4", "62", "2", "box-5");
            b6 = intakeBox(dM80, "B6", store1, "2.5", "21", "1", "box-6");
            check(coldStoreMapper.selectById(store1).getStoredKg().compareTo(new BigDecimal("180.00")) == 0,
                    "6箱入库后冷库在库应为180kg");
            check("FULFILLING".equals(deliveryMapper.selectById(dM80).getStatus()),
                    "首次入库后子单应由PLANNED转FULFILLING");

            JsonNode splitAfterIntake = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/split",
                    splitBody("split-late", "10")));
            check(!splitAfterIntake.path("success").asBoolean(true), "已开始履约的子单不能再拆");
        });

        checkpoint("CP9", "交接：一次交清、超差箱冻结在库不出库、正常箱出库、规则版本冻结", () -> {
            // 只交部分箱应拒绝（B6在库却未提交）
            JsonNode partial = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/handovers",
                    handoverBody("交接员张", "ho-partial", handoverBox(b6, "20"))));
            check(!partial.path("success").asBoolean(true), "只交部分在库箱应拒绝");
            check(partial.path("message").asText().contains("一次交清"), "应提示一次交清全部在库箱");

            // B5入库净重60，M规格V1阈值=60*2%? 否，V1为pct5+abs0.5 → 3.5；交56差异4>3.5冻结；B6交20差异0正常
            JsonNode ho = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/handovers",
                    handoverBody("交接员张", "ho-m80", handoverBox(b5, "56"), handoverBox(b6, "20"))));
            assertSuccess(ho, "交接M80子单");
            handoverM = ho.path("data").path("id").asLong();
            check(ho.path("data").path("frozenBoxCount").asInt() == 1, "应冻结1箱");
            check(ho.path("data").path("handedBoxCount").asInt() == 1, "应正常交接1箱");
            check("B5".equals(ho.path("data").path("frozenBoxNos").asText()), "冻结箱号应为B5");
            check(ho.path("data").path("varianceRuleId").asLong() == ruleV1, "交接单应冻结V1规则版本");

            check("FROZEN".equals(boxMapper.selectById(b5).getStatus()), "B5应为FROZEN");
            check("HANDED_OVER".equals(boxMapper.selectById(b6).getStatus()), "B6应为HANDED_OVER");
            check(coldStoreMapper.selectById(store1).getStoredKg().compareTo(new BigDecimal("160.00")) == 0,
                    "正常箱B6出库扣20、冻结箱B6? 应为160kg（B5的60kg冻结仍占容量）");

            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/handovers",
                    handoverBody("交接员张", "ho-m80", handoverBox(b5, "56"), handoverBox(b6, "20"))));
            assertSuccess(replay, "同requestId重放交接");
            check(replay.path("data").path("id").asLong() == handoverM, "重放应返回首次交接单");
            JsonNode second = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dM80 + "/handovers",
                    handoverBody("交接员张", "ho-m80-again", handoverBox(b5, "56"), handoverBox(b6, "20"))));
            check(!second.path("success").asBoolean(true), "新requestId重复交接应拒绝");

            // 发布V2（L收严为1%+0.5），旧交接仍冻结V1
            JsonNode v2 = json(call(HttpMethod.POST, "/api/aqua/fulfill/variance-rules",
                    ruleBody("vr-v2", vrLine("L", "1", "0.5"), vrLine("M", "5", "0.5"))));
            assertSuccess(v2, "发布V2规则");
            ruleV2 = v2.path("data").path("id").asLong();
            check(handoverMapper.selectById(handoverM).getVarianceRuleId().equals(ruleV1),
                    "V2发布后历史交接单仍冻结V1");
        });

        checkpoint("CP10", "客户首签有效：重复签收不覆盖首次回执，签收后子单DONE，冻结箱仍在库", () -> {
            JsonNode sign = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/handovers/" + handoverM + "/sign",
                    signBody("客户王", "56kg那箱差异已知悉，待处理", "sign-m80")));
            assertSuccess(sign, "客户签收M80交接单");
            check("SIGNED".equals(sign.path("data").path("status").asText()), "交接单应SIGNED");
            check("DONE".equals(deliveryMapper.selectById(dM80).getStatus()), "签收后子单应DONE");

            JsonNode signAgain = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/handovers/" + handoverM + "/sign",
                    signBody("客户赵", "冒名重复签收", "sign-m80-other")));
            assertSuccess(signAgain, "重复签收应幂等返回");
            check("客户王".equals(signAgain.path("data").path("signBy").asText()), "首签人不得被覆盖");
            check("FROZEN".equals(boxMapper.selectById(b5).getStatus()), "冻结箱仍应FROZEN在库");
        });

        checkpoint("CP11", "客户视图：看得到签收与差异、看不到内部成本；INTERNAL看全量", () -> {
            ResponseEntity<String> customerResp = call(HttpMethod.GET,
                    "/api/aqua/fulfill/orders/" + orderA + "?role=CUSTOMER", null);
            String body = customerResp.getBody();
            JsonNode customer = objectMapper.readTree(body);
            assertSuccess(customer, "客户视图查询");
            String lower = body.toLowerCase();
            for (String forbidden : Arrays.asList("price", "amount", "cost", "temp", "coldstore", "gross", "tare")) {
                check(!lower.contains(forbidden), "客户视图不得出现内部字段：" + forbidden);
            }
            JsonNode mDelivery = customer.path("data").path("deliveries").get(2);
            check(mDelivery.path("handover").path("signBy").asText().equals("客户王"),
                    "客户应看得到签收人");
            check(mDelivery.path("handover").path("signRemark").asText().contains("差异已知悉"),
                    "客户应看得到签收备注");
            check(mDelivery.path("handover").path("frozenBoxCount").asInt() == 1, "客户应看得到冻结箱数");
            JsonNode frozenBox = mDelivery.path("boxes").get(0);
            check(frozenBox.path("boxNo").asText().equals("B5")
                            || mDelivery.path("boxes").get(1).path("boxNo").asText().equals("B5"),
                    "客户应看得到箱号与差异信息");
            check(customer.toString().contains("overTolerance"), "客户应看得到逐箱是否超差标记");

            ResponseEntity<String> internalResp = call(HttpMethod.GET,
                    "/api/aqua/fulfill/orders/" + orderA + "?role=INTERNAL", null);
            String internal = internalResp.getBody().toLowerCase();
            check(internal.contains("totalamount"), "INTERNAL应看得到订单总额");
            check(internal.contains("unitprice"), "INTERNAL应看得到单价");
            check(internal.contains("intaketemp"), "INTERNAL应看得到入库温度");
            check(internal.contains("coldstoreid"), "INTERNAL应看得到冷藏库");

            ResponseEntity<String> defaultResp = call(HttpMethod.GET,
                    "/api/aqua/fulfill/orders/" + orderA, null);
            check(!defaultResp.getBody().toLowerCase().contains("totalamount"),
                    "缺省role应按最小权限等同CUSTOMER");
        });

        checkpoint("CP12", "其余子单交接签收后订单完成；完成后入库/再拆/改点全部拒绝", () -> {
            handoverAndSign(dL60, b1, "30", "ho-d1", "sign-d1");
            handoverAndSign(dChild1, b2, "15", "ho-d2", "sign-d2");
            handoverAndSign(dChild2, b3, "15", "ho-d3", "sign-d3");
            handoverAndSign(dL40, b4, "40", "ho-d4", "sign-d4");

            FulfillOrder order = fulfillOrderMapper.selectById(orderA);
            check(FulfillOrder.STATUS_COMPLETED.equals(order.getStatus()), "全部子单签收后订单应COMPLETED");
            check(order.getCompletedTime() != null, "完成时间应落库");

            JsonNode lateIntake = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/boxes",
                    boxBody("B-LATE", store1, "2.0", "11", "1", "box-late")));
            check(!lateIntake.path("success").asBoolean(true), "完成后入库应拒绝");
            JsonNode lateSplit = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/split", splitBody("split-late2", "5")));
            check(!lateSplit.path("success").asBoolean(true), "完成后再拆应拒绝");
            JsonNode lateChange = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + dL60 + "/point-changes",
                    pointChangeSubmitBody(pointB, "完成后改点", "调度员", "pc-late")));
            check(!lateChange.path("success").asBoolean(true), "完成后改点应拒绝");
        });

        checkpoint("CP13", "外部无响应：拆单不假装成功、订单停留PENDING_SYNC、重试不重复通知、履约动作全拦截", () -> {
            JsonNode down = json(call(HttpMethod.POST, "/api/aqua/fulfill/gateway/sim/down", null));
            assertSuccess(down, "模拟海水养殖管理DOWN");

            JsonNode create = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(foBId, "ff-order-b",
                            fulfillLine(rb1, "L", pointA, "50", "10"))));
            check(!create.path("success").asBoolean(true), "外部DOWN时建单必须返回失败");
            check("海水养殖管理无响应，拆单未确认，未成功".equals(create.path("message").asText()),
                    "失败文案必须明确，不能假装成功");
            orderB = fulfillOrderMapper.selectOne(new QueryWrapper<FulfillOrder>()
                    .eq("request_id", "ff-order-b").last("limit 1")).getId();
            check(FulfillOrder.STATUS_PENDING_SYNC.equals(
                    fulfillOrderMapper.selectById(orderB).getStatus()), "订单应停留PENDING_SYNC");
            FulfillOutbox pending = outboxMapper.selectOne(new QueryWrapper<FulfillOutbox>()
                    .eq("biz_id", orderB).last("limit 1"));
            check(FulfillOutbox.STATUS_PENDING.equals(pending.getStatus()), "发件箱应停留PENDING");
            check(pending.getAttempts() == 1, "应记录1次失败尝试");
            check(countEvidence(FulfillChangeEvidence.ACTION_SYNC_FAIL, null, null) >= 1,
                    "同步失败必须独立事务留证");
            check(fulfillOrderMapper.selectCount(new QueryWrapper<>()) == 2, "不得产生第二张订单");

            JsonNode beforeSim = json(call(HttpMethod.GET, "/api/aqua/fulfill/gateway/sim", null));
            int receivedBefore = beforeSim.path("data").path("receivedCount").asInt();
            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(foBId, "ff-order-b",
                            fulfillLine(rb1, "L", pointA, "50", "10"))));
            assertSuccess(replay, "同requestId重试应返回首次订单");
            check(FulfillOrder.STATUS_PENDING_SYNC.equals(replay.path("data").path("status").asText()),
                    "重试返回真实状态PENDING_SYNC");
            check(deliveryMapper.selectCount(new QueryWrapper<FulfillDelivery>()
                    .eq("order_id", orderB)) == 1, "重试不得拆出第二批子单");
            JsonNode afterSim = json(call(HttpMethod.GET, "/api/aqua/fulfill/gateway/sim", null));
            check(afterSim.path("data").path("receivedCount").asInt() == receivedBefore,
                    "重试不得再次通知外部（对端事件数不增）");

            Long pendingDeliveryId = deliveryMapper.selectList(new QueryWrapper<FulfillDelivery>()
                    .eq("order_id", orderB)).get(0).getId();
            JsonNode gateIntake = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + pendingDeliveryId + "/boxes",
                    boxBody("BB-GATE", store1, "2.0", "11", "1", "box-gate")));
            check(!gateIntake.path("success").asBoolean(true), "PENDING_SYNC订单入库应拒绝");
            JsonNode gateChange = json(call(HttpMethod.POST,
                    "/api/aqua/fulfill/deliveries/" + pendingDeliveryId + "/point-changes",
                    pointChangeSubmitBody(pointB, "未确认改点", "调度员", "pc-gate")));
            check(!gateChange.path("success").asBoolean(true), "PENDING_SYNC订单改点应拒绝");
        });

        checkpoint("CP14", "恢复后发件箱重发：只发PENDING、订单转OPEN、再次扫描与重放均不重复通知", () -> {
            // Quartz 发件箱扫描任务已注册（测试cron推到2099，不真实触发）
            check(quartzScheduler.getJobDetail(org.quartz.JobKey.jobKey(
                    "fulfillOutboxScanJob", "aqua-fulfill")) != null, "发件箱扫描Job应已注册");

            JsonNode up = json(call(HttpMethod.POST, "/api/aqua/fulfill/gateway/sim/up", null));
            assertSuccess(up, "模拟外部恢复UP");

            JsonNode scan = json(call(HttpMethod.POST, "/api/aqua/fulfill/outbox/scan", null));
            assertSuccess(scan, "手工触发发件箱扫描");
            check(scan.path("data").path("sentCount").asInt() == 1, "本次扫描应补发1条");
            check(scan.path("data").path("failedCount").asInt() == 0, "不应有失败");
            check(FulfillOrder.STATUS_OPEN.equals(fulfillOrderMapper.selectById(orderB).getStatus()),
                    "补发确认后订单应转OPEN");
            FulfillOutbox sent = outboxMapper.selectOne(new QueryWrapper<FulfillOutbox>()
                    .eq("biz_id", orderB).last("limit 1"));
            check(FulfillOutbox.STATUS_SENT.equals(sent.getStatus()) && sent.getSentTime() != null,
                    "发件箱应SENT且有确认时间");
            JsonNode sim1 = json(call(HttpMethod.GET, "/api/aqua/fulfill/gateway/sim", null));
            int count1 = sim1.path("data").path("receivedCount").asInt();
            check(count1 == 2, "恢复后对端累计应收到2个事件（订单A、B各一次）");

            JsonNode scanAgain = json(call(HttpMethod.POST, "/api/aqua/fulfill/outbox/scan", null));
            assertSuccess(scanAgain, "再次扫描");
            check(scanAgain.path("data").path("sentCount").asInt() == 0, "SENT记录必须跳过，不再发送");
            JsonNode sim2 = json(call(HttpMethod.GET, "/api/aqua/fulfill/gateway/sim", null));
            check(sim2.path("data").path("receivedCount").asInt() == count1,
                    "对端按eventId幂等，事件数不得增加");

            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/fulfill/orders",
                    fulfillOrderBody(foBId, "ff-order-b",
                            fulfillLine(rb1, "L", pointA, "50", "10"))));
            assertSuccess(replay, "恢复后同requestId重放");
            check(replay.path("data").path("id").asLong() == orderB, "仍返回同一订单");
            check(deliveryMapper.selectCount(new QueryWrapper<FulfillDelivery>()
                    .eq("order_id", orderB)) == 1, "子单数不变");

            // 补齐订单B的入库/交接/签收（V2下L阈值=50*1%+0.5=1.0，交50差异0正常）
            Long dB = deliveryMapper.selectList(new QueryWrapper<FulfillDelivery>()
                    .eq("order_id", orderB)).get(0).getId();
            bb1 = intakeBox(dB, "BB1", store1, "2.0", "52", "2", "box-b1");
            handoverAndSign(dB, bb1, "50", "ho-b1", "sign-b1");
            check(FulfillOrder.STATUS_COMPLETED.equals(fulfillOrderMapper.selectById(orderB).getStatus()),
                    "订单B补齐履约后应COMPLETED");
        });

        checkpoint("CP15", "全程留证与账实闭环：证据计数、冷库重量恰等于冻结箱净重合计", () -> {
            check(countEvidence(FulfillChangeEvidence.ACTION_ORDER_CREATE, null, null) == 2,
                    "ORDER_CREATE证据应为2条");
            check(countEvidence(FulfillChangeEvidence.ACTION_SYNC_OK, null, null) == 2,
                    "SYNC_OK证据应为2条");
            check(countEvidence(FulfillChangeEvidence.ACTION_ORDER_COMPLETE, null, null) == 2,
                    "ORDER_COMPLETE证据应为2条");
            check(countEvidence(FulfillChangeEvidence.ACTION_BOX_FREEZE, null, null) == 1,
                    "BOX_FREEZE证据应为1条");
            check(countEvidence(FulfillChangeEvidence.ACTION_POINT_CHANGE_APPROVE, null, null) == 1,
                    "改点批准证据应为1条");
            check(countEvidence(FulfillChangeEvidence.ACTION_POINT_CHANGE_REJECT, null, null) >= 1,
                    "改点驳回证据至少1条");
            check(countEvidence(FulfillChangeEvidence.ACTION_HANDOVER_SIGN, null, null) == 6,
                    "HANDOVER_SIGN证据应为6条（订单A5+订单B1）");
            check(countEvidence(FulfillChangeEvidence.ACTION_BOX_HANDOVER,
                    FulfillChangeEvidence.ENTITY_FULFILL_BOX, null) == 6,
                    "箱正常交接证据应为6条（冷库扣重证据另计，不含在此）");
            check(countEvidence(FulfillChangeEvidence.ACTION_SYNC_FAIL, null, null) >= 1,
                    "SYNC_FAIL证据至少1条");

            JsonNode ev = json(call(HttpMethod.GET,
                    "/api/aqua/fulfill/evidence?entityType=FULFILL_OUTBOX&requestId=ff-order-b", null));
            assertSuccess(ev, "证据三参数查询");
            check(ev.path("data").size() >= 2, "订单B发件箱证据应含失败与成功各至少一条");

            BigDecimal frozenNetSum = BigDecimal.ZERO;
            for (FulfillBox box : boxMapper.selectList(new QueryWrapper<FulfillBox>()
                    .eq("status", FulfillBox.STATUS_FROZEN))) {
                frozenNetSum = frozenNetSum.add(box.getNetKg());
            }
            ColdStore store = coldStoreMapper.selectById(store1);
            check(store.getStoredKg().compareTo(frozenNetSum) == 0,
                    "冷库在库重量" + store.getStoredKg() + "必须恰等于全部冻结箱净重合计" + frozenNetSum
                            + "（正常箱均已出库）");
        });

        printReport();
    }

    // ------------------------------------------------------------------
    // 前置捕捞数据：直接插已完成的捕捞订单与预约（跨模块只读复用 fishing mapper，不改 aqua-8 行为）
    // ------------------------------------------------------------------

    private Long foCId;
    private Long rc1;

    private void seedFishingOrders() {
        FishingOrder foA = new FishingOrder();
        foA.setOrderNo("FO-A");
        foA.setCustomerName("海丰客户");
        foA.setStatus(FishingOrder.STATUS_COMPLETED);
        foA.setOperator("system-seed");
        foA.setRequestId("seed-fo-a");
        fishingOrderMapper.insert(foA);
        foAId = foA.getId();
        ra1 = seedReservation(foA, "FO-A-R1", "L", "500", "1000", "100", "100", "seed-ra1");
        ra2 = seedReservation(foA, "FO-A-R2", "M", "300", "500", "80", "80", "seed-ra2");

        FishingOrder foB = new FishingOrder();
        foB.setOrderNo("FO-B");
        foB.setCustomerName("海丰客户");
        foB.setStatus(FishingOrder.STATUS_COMPLETED);
        foB.setOperator("system-seed");
        foB.setRequestId("seed-fo-b");
        fishingOrderMapper.insert(foB);
        foBId = foB.getId();
        rb1 = seedReservation(foB, "FO-B-R1", "L", "500", "1000", "50", "50", "seed-rb1");

        FishingOrder foC = new FishingOrder();
        foC.setOrderNo("FO-C");
        foC.setCustomerName("在途客户");
        foC.setStatus(FishingOrder.STATUS_OPEN);
        foC.setOperator("system-seed");
        foC.setRequestId("seed-fo-c");
        fishingOrderMapper.insert(foC);
        foCId = foC.getId();
        rc1 = seedReservation(foC, "FO-C-R1", "L", "500", "1000", "30", "0", "seed-rc1");
    }

    private Long seedReservation(FishingOrder order, String resvNo, String spec, String minG, String maxG,
                                 String planned, String actual, String requestId) {
        FishingReservation r = new FishingReservation();
        r.setResvNo(resvNo);
        r.setOrderId(order.getId());
        r.setSpecCode(spec);
        r.setWeightMinG(new BigDecimal(minG));
        r.setWeightMaxG(new BigDecimal(maxG));
        r.setDeliveryTime(LocalDateTime.of(2026, 10, 6, 10, 0));
        r.setPlannedQtyKg(new BigDecimal(planned));
        r.setActualQtyKg(new BigDecimal(actual));
        r.setStatus(FishingReservation.STATUS_COMPLETED);
        r.setRequestId(requestId);
        fishingReservationMapper.insert(r);
        return r.getId();
    }

    // ------------------------------------------------------------------
    // 履约流程辅助
    // ------------------------------------------------------------------

    private Long intakeBox(Long deliveryId, String boxNo, Long storeId, String temp,
                           String gross, String tare, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST,
                "/api/aqua/fulfill/deliveries/" + deliveryId + "/boxes",
                boxBody(boxNo, storeId, temp, gross, tare, requestId)));
        assertSuccess(resp, "箱" + boxNo + "入库");
        return resp.path("data").path("id").asLong();
    }

    private void handoverAndSign(Long deliveryId, Long boxId, String handoverNet,
                                 String hoRequestId, String signRequestId) throws Exception {
        JsonNode ho = json(call(HttpMethod.POST,
                "/api/aqua/fulfill/deliveries/" + deliveryId + "/handovers",
                handoverBody("交接员张", hoRequestId, handoverBox(boxId, handoverNet))));
        assertSuccess(ho, "子单" + deliveryId + "交接");
        Long hoId = ho.path("data").path("id").asLong();
        JsonNode sign = json(call(HttpMethod.POST,
                "/api/aqua/fulfill/handovers/" + hoId + "/sign",
                signBody("客户王", "签收无异议", signRequestId)));
        assertSuccess(sign, "子单" + deliveryId + "签收");
    }

    private void assertAmountConserved(Long orderId) {
        FulfillOrder order = fulfillOrderMapper.selectById(orderId);
        BigDecimal sum = BigDecimal.ZERO;
        for (FulfillDelivery d : deliveriesOfOrder(orderId)) {
            sum = sum.add(d.getLineAmount());
        }
        check(sum.compareTo(order.getTotalAmount()) == 0,
                "再拆/改点后全部子单行金额合计必须仍等于订单总额" + order.getTotalAmount() + "，实际" + sum);
    }

    private List<FulfillDelivery> deliveriesOfOrder(Long orderId) {
        return deliveryMapper.selectList(new QueryWrapper<FulfillDelivery>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private long countEvidence(String action, String entityType, Long entityId) {
        QueryWrapper<FulfillChangeEvidence> wrapper = new QueryWrapper<FulfillChangeEvidence>()
                .eq("action", action);
        if (entityType != null) {
            wrapper.eq("entity_type", entityType);
        }
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long count = evidenceMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    // ------------------------------------------------------------------
    // 请求体构造
    // ------------------------------------------------------------------

    private Map<String, Object> pointBody(String no, String name, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("pointNo", no);
        body.put("pointName", name);
        body.put("contactPerson", "联系人");
        body.put("operator", "调度员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> vrLine(String spec, String pct, String abs) {
        Map<String, Object> line = new HashMap<>();
        line.put("specCode", spec);
        line.put("tolerancePct", new BigDecimal(pct));
        line.put("toleranceAbsKg", new BigDecimal(abs));
        return line;
    }

    private Map<String, Object> ruleBody(String requestId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("lines", Arrays.asList(lines));
        body.put("publishedBy", "品控主管");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> storeBody(String no, String name, String capacity, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("storeNo", no);
        body.put("storeName", name);
        body.put("capacityKg", new BigDecimal(capacity));
        body.put("operator", "仓管员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> fulfillLine(Long reservationId, String spec, Long pointId,
                                            String qty, String price) {
        Map<String, Object> line = new HashMap<>();
        line.put("reservationId", reservationId);
        line.put("specCode", spec);
        line.put("deliveryPointId", pointId);
        line.put("plannedNetKg", new BigDecimal(qty));
        line.put("unitPrice", new BigDecimal(price));
        return line;
    }

    private Map<String, Object> fulfillOrderBody(Long fishingOrderId, String requestId,
                                                 Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("fishingOrderId", fishingOrderId);
        body.put("lines", Arrays.asList(lines));
        body.put("operator", "履约调度员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> splitBody(String requestId, String... childQtys) {
        List<Map<String, Object>> children = new ArrayList<>();
        for (String qty : childQtys) {
            Map<String, Object> child = new HashMap<>();
            child.put("plannedNetKg", new BigDecimal(qty));
            children.add(child);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("children", children);
        body.put("operator", "履约调度员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> pointChangeSubmitBody(Long toPointId, String reason,
                                                      String submittedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("toPointId", toPointId);
        body.put("reason", reason);
        body.put("submittedBy", submittedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> reviewBody(String approvedBy, String remark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("approvedBy", approvedBy);
        body.put("approveRemark", remark);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> boxBody(String boxNo, Long storeId, String temp,
                                        String gross, String tare, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("boxNo", boxNo);
        body.put("coldStoreId", storeId);
        body.put("intakeTemp", new BigDecimal(temp));
        body.put("grossKg", new BigDecimal(gross));
        body.put("tareKg", new BigDecimal(tare));
        body.put("weighedBy", "仓管员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> handoverBox(Long boxId, String net) {
        Map<String, Object> box = new HashMap<>();
        box.put("boxId", boxId);
        box.put("handoverNetKg", new BigDecimal(net));
        return box;
    }

    private Map<String, Object> handoverBody(String handoverBy, String requestId,
                                             Map<String, Object>... boxes) {
        Map<String, Object> body = new HashMap<>();
        body.put("boxes", Arrays.asList(boxes));
        body.put("handoverBy", handoverBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> signBody(String signBy, String remark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("signBy", signBy);
        body.put("signRemark", remark);
        body.put("requestId", requestId);
        return body;
    }

    // ------------------------------------------------------------------
    // HTTP 与 checkpoint 脚手架（与其余验收测试同构）
    // ------------------------------------------------------------------

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 渔获订单履约验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=========================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
