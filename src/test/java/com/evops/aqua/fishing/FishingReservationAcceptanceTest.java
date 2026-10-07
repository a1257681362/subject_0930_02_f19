package com.evops.aqua.fishing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.FishingBin;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingGradingRule;
import com.evops.aqua.fishing.entity.FishingLoad;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.entity.FishingVariance;
import com.evops.aqua.fishing.mapper.FishingBinMapper;
import com.evops.aqua.fishing.mapper.FishingChangeEvidenceMapper;
import com.evops.aqua.fishing.mapper.FishingGradingRuleMapper;
import com.evops.aqua.fishing.mapper.FishingLoadMapper;
import com.evops.aqua.fishing.mapper.FishingNotificationMapper;
import com.evops.aqua.fishing.mapper.FishingOrderMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.evops.aqua.fishing.mapper.FishingVarianceMapper;
import com.evops.aqua.fishing.mapper.FishingVehicleChangeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 捕捞预约与规格分仓验收（aqua-8）。
 * 主线：发布分级规则版本 → 按规格登记库位 → 客户订单按规格/重量区间/交付时间拆分预约
 * → 预约锁定占用库位（他单不得占用）→ 现场按实际规格判级分仓（库位状态与待检标识同步）
 * → 检验复位 → 临时换车登记原因并触发客户通知签收回执 → 完成生成短装/超装差异单
 * → 订单完成冻结当时分级规则快照（新版本不影响已完成订单）
 * → 补传/补录/重试幂等只形成一条可用结果 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FishingReservationAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FishingGradingRuleMapper ruleMapper;
    @Autowired
    private FishingBinMapper binMapper;
    @Autowired
    private FishingOrderMapper orderMapper;
    @Autowired
    private FishingReservationMapper reservationMapper;
    @Autowired
    private FishingLoadMapper loadMapper;
    @Autowired
    private FishingVarianceMapper varianceMapper;
    @Autowired
    private FishingVehicleChangeMapper vehicleChangeMapper;
    @Autowired
    private FishingNotificationMapper notificationMapper;
    @Autowired
    private FishingChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long ruleV1;
    private Long ruleV2;
    private Long binL1;      // L 规格，容量180kg
    private Long binM1;      // M 规格，容量500kg
    private Long order1;     // 海天酒楼：L 200kg + M 150kg
    private Long order2;     // 鲜味坊：L 100kg
    private Long resv1L;     // 订单1-L预约，计划200kg
    private Long resv1M;     // 订单1-M预约，计划150kg
    private Long resv2L;     // 订单2-L预约，计划100kg
    private Long notice1;

    @Test
    void acceptanceFishingReservation() {
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

        checkpoint("CP1", "发布分级规则V1（S/M/L半开区间）；区间重叠被拒；requestId幂等", () -> {
            JsonNode bad = json(call(HttpMethod.POST, "/api/aqua/fishing/grading-rules",
                    ruleBody("frule-bad", line("L", "500", "800"), line("X", "700", "900"))));
            check(!bad.path("success").asBoolean(true), "区间重叠的规则版本应被拒绝");
            check(bad.path("message").asText().contains("重叠"), "拒绝原因应说明区间重叠");

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/grading-rules",
                    ruleBody("frule-v1", line("S", "100", "300"), line("M", "300", "500"), line("L", "500", "800"))));
            assertSuccess(resp, "发布规则V1");
            ruleV1 = resp.path("data").path("id").asLong();
            check("FGR-V1".equals(resp.path("data").path("ruleNo").asText()), "首个版本应为FGR-V1");
            check(FishingGradingRule.STATUS_ACTIVE.equals(
                    ruleMapper.selectById(ruleV1).getStatus()), "V1应生效");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fishing/grading-rules",
                    ruleBody("frule-v1", line("S", "100", "300"), line("M", "300", "500"), line("L", "500", "800"))));
            assertSuccess(again, "重复发布V1");
            check(again.path("data").path("id").asLong() == ruleV1, "同requestId重复发布应返回同一版本");
            check(ruleMapper.selectCount(new QueryWrapper<>()) == 1L, "重复发布不得产生第二个版本");
        });

        checkpoint("CP2", "按规格登记库位；requestId幂等；库位编号重复被拒", () -> {
            binL1 = registerBin("BIN-L1", "L", "180", "fbin-l1");
            binM1 = registerBin("BIN-M1", "M", "500", "fbin-m1");
            registerBin("BIN-S1", "S", "200", "fbin-s1");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fishing/bins",
                    binBody("BIN-L1", "L", "180", "fbin-l1")));
            assertSuccess(again, "重复登记BIN-L1");
            check(again.path("data").path("id").asLong() == binL1, "同requestId重复登记应返回同一库位");

            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/fishing/bins",
                    binBody("BIN-L1", "L", "300", "fbin-l1-dup")));
            check(!dup.path("success").asBoolean(true), "库位编号重复应被拒绝");
            check(dup.path("message").asText().contains("已存在"), "拒绝原因应说明编号已存在");
        });

        checkpoint("CP3", "客户订单按规格/重量区间/交付时间拆分预约；补传重试幂等；超规则区间被拒", () -> {
            Map<String, Object> noOperator = orderBody("海天酒楼", "沪A·1111", null,
                    line("L", "550", "750", "2026-10-06T08:00:00", "200"));
            noOperator.remove("operator");
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/fishing/orders", noOperator));
            check(!denied.path("success").asBoolean(true), "缺操作人创建订单应被参数校验拒绝");

            JsonNode outOfRange = json(call(HttpMethod.POST, "/api/aqua/fishing/orders",
                    orderBody("海天酒楼", null, "ford-bad",
                            line("L", "100", "200", "2026-10-06T08:00:00", "50"))));
            check(!outOfRange.path("success").asBoolean(true), "重量区间超出分级规则区间应被拒绝");
            check(outOfRange.path("message").asText().contains("超出分级规则区间"), "拒绝原因应说明超出规则区间");

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/orders",
                    orderBody("海天酒楼", "沪A·1111", "ford-1",
                            line("L", "550", "750", "2026-10-06T08:00:00", "200"),
                            line("M", "320", "480", "2026-10-06T08:00:00", "150"))));
            assertSuccess(resp, "创建订单1");
            order1 = resp.path("data").path("id").asLong();
            List<FishingReservation> reservations = reservationsOf(order1);
            check(reservations.size() == 2, "订单1应拆分为2条预约，实际" + reservations.size());
            resv1L = reservations.get(0).getId();
            resv1M = reservations.get(1).getId();
            check("L".equals(reservations.get(0).getSpecCode())
                    && "M".equals(reservations.get(1).getSpecCode()), "预约应按行拆分规格");
            check("沪A·1111".equals(reservations.get(0).getVehicleNo()), "预约应继承订单登记车辆");

            JsonNode retry = json(call(HttpMethod.POST, "/api/aqua/fishing/orders",
                    orderBody("海天酒楼", "沪A·1111", "ford-1",
                            line("L", "550", "750", "2026-10-06T08:00:00", "200"),
                            line("M", "320", "480", "2026-10-06T08:00:00", "150"))));
            assertSuccess(retry, "补传订单1");
            check(retry.path("data").path("id").asLong() == order1, "同requestId补传应返回同一订单");
            check(reservationsOf(order1).size() == 2, "补传重试不得拆出第二批预约");
        });

        checkpoint("CP4", "第二个客户订单（鲜味坊，L规格100kg）", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/orders",
                    orderBody("鲜味坊", "闽D·3333", "ford-2",
                            line("L", "500", "800", "2026-10-07T09:00:00", "100"))));
            assertSuccess(resp, "创建订单2");
            order2 = resp.path("data").path("id").asLong();
            List<FishingReservation> reservations = reservationsOf(order2);
            check(reservations.size() == 1, "订单2应拆分为1条预约");
            resv2L = reservations.get(0).getId();
        });

        checkpoint("CP5", "预约锁定占用库位：他单占用被拒；草稿登记分仓被拒", () -> {
            JsonNode lock1 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/lock",
                    lockBody(binL1, null, "调度员", "flock-1")));
            assertSuccess(lock1, "锁定订单1-L预约");
            check(lock1.path("data").path("status").asText().equals(FishingReservation.STATUS_LOCKED),
                    "锁定后预约应为LOCKED");
            FishingBin bin = binMapper.selectById(binL1);
            check(order1.equals(bin.getOccupiedOrderId()) && resv1L.equals(bin.getOccupiedResvId()),
                    "BIN-L1应被订单1占用");

            JsonNode lock2 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1M + "/lock",
                    lockBody(binM1, null, "调度员", "flock-2")));
            assertSuccess(lock2, "锁定订单1-M预约");

            JsonNode conflict = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv2L + "/lock",
                    lockBody(binL1, null, "调度员", "flock-3")));
            check(!conflict.path("success").asBoolean(true), "他单已占用库位应拒绝锁定");
            check(conflict.path("message").asText().contains("不能被其他订单占用"), "拒绝原因应说明库位被他单占用");

            JsonNode draftLoad = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv2L + "/loads",
                    loadBody("600", "50", "现场员", "fload-5")));
            check(!draftLoad.path("success").asBoolean(true), "草稿（未锁定）预约登记分仓应被拒绝");
        });

        checkpoint("CP6", "现场按实际规格分仓：判级入仓、库位状态与待检标识同步、容量拦截、补录幂等", () -> {
            JsonNode load1 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/loads",
                    loadBody("620", "180", "现场员", "fload-1")));
            assertSuccess(load1, "L预约装180kg@620g");
            check("L".equals(load1.path("data").path("specCode").asText()), "620g应判为L");
            check(load1.path("data").path("binId").asLong() == binL1, "应入锁定库位BIN-L1");
            FishingBin bin = binMapper.selectById(binL1);
            check(FishingBin.STATUS_FULL.equals(bin.getStatus()) && Boolean.TRUE.equals(bin.getPendingInspection()),
                    "装满后库位应为FULL且待检标识同步置位，实际" + bin.getStatus() + "/" + bin.getPendingInspection());

            JsonNode retry = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/loads",
                    loadBody("620", "180", "现场员", "fload-1")));
            assertSuccess(retry, "补录重试");
            check(retry.path("data").path("id").asLong() == load1.path("data").path("id").asLong(),
                    "同requestId补录应返回同一分仓记录");
            check(loadsOf(resv1L).size() == 1, "补录重试只应形成一条可用记录");
            check(binMapper.selectById(binL1).getLoadedKg().compareTo(new BigDecimal("180.00")) == 0,
                    "补录重试不得重复加账");

            JsonNode load2 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/loads",
                    loadBody("480", "30", "现场员", "fload-2")));
            assertSuccess(load2, "L预约装30kg@480g");
            check("M".equals(load2.path("data").path("specCode").asText()), "480g应判为M");
            check(load2.path("data").path("binId").asLong() == binM1, "实际规格M应入M仓（同订单允许共用）");

            JsonNode overload = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/loads",
                    loadBody("620", "50", "现场员", "fload-3")));
            check(!overload.path("success").asBoolean(true), "超库位容量应被拒绝");
            check(overload.path("message").asText().contains("容量不足"), "拒绝原因应说明容量不足");

            JsonNode load4 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1M + "/loads",
                    loadBody("300", "140", "现场员", "fload-4")));
            assertSuccess(load4, "M预约装140kg@300g");
            check("M".equals(load4.path("data").path("specCode").asText()),
                    "300g恰为S/M分界，半开区间下沿应归M档");
            FishingBin binM = binMapper.selectById(binM1);
            check(FishingBin.STATUS_LOADING.equals(binM.getStatus())
                            && Boolean.TRUE.equals(binM.getPendingInspection()),
                    "BIN-M1应为LOADING且待检标识同步置位");
            check(binM.getLoadedKg().compareTo(new BigDecimal("170.00")) == 0, "BIN-M1应累计170kg");
        });

        checkpoint("CP7", "待检未检验不得完成预约；检验后状态与待检标识同步复位；重复检验幂等", () -> {
            JsonNode early = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/complete",
                    actionBody("调度员", "fcomp-early")));
            check(!early.path("success").asBoolean(true), "库位待检时完成预约应被拒绝");
            check(early.path("message").asText().contains("待检验"), "拒绝原因应说明须先检验");

            JsonNode inspect = json(call(HttpMethod.POST, "/api/aqua/fishing/bins/" + binL1 + "/inspect",
                    actionBody("质检员", "finsp-1")));
            assertSuccess(inspect, "检验BIN-L1");
            FishingBin bin = binMapper.selectById(binL1);
            check(FishingBin.STATUS_CLEARED.equals(bin.getStatus()) && !bin.getPendingInspection(),
                    "检验后应为CLEARED且待检标识同步清除");
            check("质检员".equals(bin.getInspectedBy()), "应记录检验人");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fishing/bins/" + binL1 + "/inspect",
                    actionBody("质检员", "finsp-3")));
            assertSuccess(again, "重复检验BIN-L1");
            check(countEvidence(FishingChangeEvidence.ACTION_BIN_INSPECT, binL1) == 1L,
                    "重复检验不得重复留证");

            JsonNode inspectM = json(call(HttpMethod.POST, "/api/aqua/fishing/bins/" + binM1 + "/inspect",
                    actionBody("质检员", "finsp-2")));
            assertSuccess(inspectM, "检验BIN-M1");
        });

        checkpoint("CP8", "临时换车：原因必填、触发客户通知、签收留回执、重试不重复", () -> {
            Map<String, Object> noReason = vehicleBody("沪B·2222", null, "调度员", "fveh-bad");
            JsonNode denied = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/reservations/" + resv1L + "/vehicle-change", noReason));
            check(!denied.path("success").asBoolean(true), "缺换车原因应被参数校验拒绝");

            JsonNode change = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/reservations/" + resv1L + "/vehicle-change",
                    vehicleBody("沪B·2222", "原车辆途中故障", "调度员", "fveh-1")));
            assertSuccess(change, "临时换车");
            check("沪A·1111".equals(change.path("data").path("oldVehicleNo").asText()), "应记录原车牌");
            check("沪B·2222".equals(reservationMapper.selectById(resv1L).getVehicleNo()), "预约车辆应更新");

            List<FishingNotification> notices = notificationsOf(order1);
            check(notices.size() == 1, "换车应触发一条客户通知，实际" + notices.size());
            notice1 = notices.get(0).getId();
            check(FishingNotification.STATUS_SENT.equals(notices.get(0).getStatus()), "通知应为已发送");
            check(notices.get(0).getMessage().contains("原车辆途中故障"), "通知应携带换车原因");
            check("海天酒楼".equals(notices.get(0).getCustomerName()), "通知应发给订单客户");

            JsonNode retry = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/reservations/" + resv1L + "/vehicle-change",
                    vehicleBody("沪B·2222", "原车辆途中故障", "调度员", "fveh-1")));
            assertSuccess(retry, "换车重试");
            check(retry.path("data").path("id").asLong() == change.path("data").path("id").asLong(),
                    "同requestId重试应返回同一换车记录");
            check(notificationsOf(order1).size() == 1, "重试不得重复通知");

            JsonNode ack = json(call(HttpMethod.POST, "/api/aqua/fishing/notifications/" + notice1 + "/ack",
                    ackBody("海天酒楼-王经理", "已知悉，按新车交接", "fack-1")));
            assertSuccess(ack, "客户签收");
            check(FishingNotification.STATUS_ACKED.equals(ack.path("data").path("status").asText()),
                    "签收后通知应为ACKED");

            JsonNode reAck = json(call(HttpMethod.POST, "/api/aqua/fishing/notifications/" + notice1 + "/ack",
                    ackBody("海天酒楼-李四", "重复签收", "fack-2")));
            assertSuccess(reAck, "重复签收");
            check("海天酒楼-王经理".equals(reAck.path("data").path("ackBy").asText()),
                    "重复签收不得覆盖首次回执");
        });

        checkpoint("CP9", "完成预约：超装/短装自动生成差异单；库位释放；重复完成幂等", () -> {
            JsonNode done1 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/complete",
                    actionBody("调度员", "fcomp-1")));
            assertSuccess(done1, "完成L预约");
            FishingVariance over = varianceOf(resv1L);
            check(over != null && FishingVariance.TYPE_OVER.equals(over.getVarianceType()),
                    "实际210kg>计划200kg应生成超装差异单");
            check(over.getDiffQtyKg().compareTo(new BigDecimal("10.00")) == 0, "超装差异应为10kg");
            FishingBin bin = binMapper.selectById(binL1);
            check(FishingBin.STATUS_EMPTY.equals(bin.getStatus()) && bin.getOccupiedOrderId() == null
                            && bin.getLoadedKg().compareTo(BigDecimal.ZERO) == 0,
                    "完成交付后库位应腾空释放");

            JsonNode done2 = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1M + "/complete",
                    actionBody("调度员", "fcomp-2")));
            assertSuccess(done2, "完成M预约");
            FishingVariance shortVar = varianceOf(resv1M);
            check(shortVar != null && FishingVariance.TYPE_SHORT.equals(shortVar.getVarianceType()),
                    "实际140kg<计划150kg应生成短装差异单");
            check(shortVar.getDiffQtyKg().compareTo(new BigDecimal("10.00")) == 0, "短装差异应为10kg");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/complete",
                    actionBody("调度员", "fcomp-1-retry")));
            assertSuccess(again, "重复完成L预约");
            check(varianceMapper.selectCount(new QueryWrapper<FishingVariance>()
                    .eq("reservation_id", resv1L)) == 1L, "重复完成不得生成第二张差异单");
        });

        checkpoint("CP10", "订单完成冻结当时分级规则：发布V2后已完成订单仍保留V1快照", () -> {
            FishingOrder order = orderMapper.selectById(order1);
            check(FishingOrder.STATUS_COMPLETED.equals(order.getStatus()), "全部预约完成后订单应完成");
            check(ruleV1.equals(order.getGradingRuleId()), "订单1应冻结当时生效规则V1");
            check(order.getGradingRuleSnapshot() != null && order.getGradingRuleSnapshot().contains("FGR-V1"),
                    "订单1应保留V1快照");

            JsonNode v2 = json(call(HttpMethod.POST, "/api/aqua/fishing/grading-rules",
                    ruleBody("frule-v2", line("S", "100", "250"), line("M", "250", "450"), line("L", "450", "900"))));
            assertSuccess(v2, "发布规则V2");
            ruleV2 = v2.path("data").path("id").asLong();
            check(FishingGradingRule.STATUS_DISABLED.equals(ruleMapper.selectById(ruleV1).getStatus()),
                    "V2发布后V1应停用");

            JsonNode view = json(call(HttpMethod.GET, "/api/aqua/fishing/orders/" + order1, null));
            check("FGR-V1".equals(view.path("data").path("gradingRuleSnapshot").path("ruleNo").asText()),
                    "已完成订单1应保留当时分级规则V1，不受V2影响");
        });

        checkpoint("CP11", "库位释放后他单可用；数量一致无差异单；订单2完成冻结V2", () -> {
            JsonNode lock = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv2L + "/lock",
                    lockBody(null, null, "调度员", "flock-4")));
            assertSuccess(lock, "订单2自动选仓锁定");
            check(lock.path("data").path("binId").asLong() == binL1, "BIN-L1释放后应可被订单2占用");

            JsonNode load = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv2L + "/loads",
                    loadBody("700", "100", "现场员", "fload-6")));
            assertSuccess(load, "订单2装100kg@700g");
            check("L".equals(load.path("data").path("specCode").asText()), "700g按V2应判为L");
            check(load.path("data").path("gradingRuleId").asLong() == ruleV2, "分仓判级应使用当时生效的V2");

            JsonNode inspect = json(call(HttpMethod.POST, "/api/aqua/fishing/bins/" + binL1 + "/inspect",
                    actionBody("质检员", "finsp-4")));
            assertSuccess(inspect, "检验BIN-L1");

            JsonNode done = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv2L + "/complete",
                    actionBody("调度员", "fcomp-3")));
            assertSuccess(done, "完成订单2预约");
            check(varianceOf(resv2L) == null, "实际=计划不应生成差异单");
            FishingOrder order = orderMapper.selectById(order2);
            check(FishingOrder.STATUS_COMPLETED.equals(order.getStatus()), "订单2应完成");
            check(ruleV2.equals(order.getGradingRuleId()), "订单2应冻结当时生效规则V2");
        });

        checkpoint("CP12", "全程留证可追溯；补传补录重试只形成一条可用结果", () -> {
            check(countEvidence(FishingChangeEvidence.ACTION_ORDER_CREATE, order1) == 1L,
                    "订单1创建应留证一次");
            check(countEvidence(FishingChangeEvidence.ACTION_RESV_LOCK, resv1L) >= 1L, "预约锁定应留证");
            QueryWrapper<FishingChangeEvidence> loadEvidence = new QueryWrapper<FishingChangeEvidence>()
                    .eq("entity_type", FishingChangeEvidence.ENTITY_LOAD)
                    .eq("action", FishingChangeEvidence.ACTION_LOAD_RECORD);
            check(evidenceMapper.selectCount(loadEvidence) == 4L,
                    "成功分仓应留证4次（180kg/30kg/140kg/100kg），实际"
                            + evidenceMapper.selectCount(loadEvidence));
            check(countEvidence(FishingChangeEvidence.ACTION_VARIANCE_CREATE, null) == 2L,
                    "差异单应留证2次（超装+短装）");
            check(countEvidence(FishingChangeEvidence.ACTION_VEHICLE_CHANGE, null) == 1L, "换车应留证1次");
            check(countEvidence(FishingChangeEvidence.ACTION_NOTIFY_SEND, null) == 1L, "通知发送应留证1次");
            check(countEvidence(FishingChangeEvidence.ACTION_NOTIFY_ACK, null) == 1L, "通知签收应留证1次");
            check(countEvidence(FishingChangeEvidence.ACTION_BIN_RELEASE, null) == 3L,
                    "库位释放应留证3次（BIN-L1两次+BIN-M1）");
            List<FishingChangeEvidence> byRequest = evidenceMapper.selectList(
                    new QueryWrapper<FishingChangeEvidence>().eq("request_id", "ford-1"));
            check(byRequest.size() == 1 && FishingChangeEvidence.ACTION_ORDER_CREATE.equals(byRequest.get(0).getAction()),
                    "订单补传请求号应只对应首次创建证据");
            List<FishingChangeEvidence> splitEvidence = evidenceMapper.selectList(
                    new QueryWrapper<FishingChangeEvidence>().eq("request_id", "ford-1#1"));
            check(splitEvidence.size() == 1 && FishingChangeEvidence.ACTION_RESV_CREATE.equals(splitEvidence.get(0).getAction()),
                    "拆分预约应按派生请求号留证");

            JsonNode reOrder = json(call(HttpMethod.POST, "/api/aqua/fishing/orders",
                    orderBody("海天酒楼", "沪A·1111", "ford-1",
                            line("L", "550", "750", "2026-10-06T08:00:00", "200"),
                            line("M", "320", "480", "2026-10-06T08:00:00", "150"))));
            check(reOrder.path("data").path("id").asLong() == order1, "订单补传应返回同一订单");
            check(reservationsOf(order1).size() == 2, "订单补传不得新增预约");
            JsonNode reLoad = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resv1L + "/loads",
                    loadBody("620", "180", "现场员", "fload-1")));
            check(reLoad.path("success").asBoolean(false), "分仓补录应成功返回首次记录");
            check(loadsOf(resv1L).size() == 2, "分仓补录重试不得新增记录");
            check(vehicleChangeMapper.selectCount(new QueryWrapper<>()) == 1L, "换车记录应只有一条");
            check(notificationMapper.selectCount(new QueryWrapper<>()) == 1L, "客户通知应只有一条");
            check(varianceMapper.selectCount(new QueryWrapper<>()) == 2L, "差异单应只有两张");
        });

        printReport();
    }

    // ------------------------------------------------------------------
    // 请求构造与断言辅助
    // ------------------------------------------------------------------

    private Long registerBin(String binNo, String spec, String capacity, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/bins",
                binBody(binNo, spec, capacity, requestId)));
        assertSuccess(resp, "登记库位" + binNo);
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> ruleBody(String requestId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("lines", Arrays.asList(lines));
        body.put("publishedBy", "品控主管");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> line(String spec, String minG, String maxG) {
        Map<String, Object> line = new HashMap<>();
        line.put("specCode", spec);
        line.put("minWeightG", new BigDecimal(minG));
        line.put("maxWeightG", new BigDecimal(maxG));
        return line;
    }

    private Map<String, Object> binBody(String binNo, String spec, String capacity, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("binNo", binNo);
        body.put("specCode", spec);
        body.put("capacityKg", new BigDecimal(capacity));
        body.put("operator", "仓管员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> orderBody(String customer, String vehicleNo, String requestId,
                                          Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerName", customer);
        body.put("vehicleNo", vehicleNo);
        body.put("lines", Arrays.asList(lines));
        body.put("operator", "接单员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> line(String spec, String minG, String maxG, String deliveryTime, String qtyKg) {
        Map<String, Object> line = new HashMap<>();
        line.put("specCode", spec);
        line.put("weightMinG", new BigDecimal(minG));
        line.put("weightMaxG", new BigDecimal(maxG));
        line.put("deliveryTime", deliveryTime);
        line.put("plannedQtyKg", new BigDecimal(qtyKg));
        return line;
    }

    private Map<String, Object> lockBody(Long binId, String vehicleNo, String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("binId", binId);
        body.put("vehicleNo", vehicleNo);
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> loadBody(String avgWeightG, String weightKg, String loadedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("actualAvgWeightG", new BigDecimal(avgWeightG));
        body.put("weightKg", new BigDecimal(weightKg));
        body.put("loadedBy", loadedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> actionBody(String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> vehicleBody(String newVehicleNo, String reason, String changedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("newVehicleNo", newVehicleNo);
        body.put("reason", reason);
        body.put("changedBy", changedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> ackBody(String ackBy, String ackRemark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("ackBy", ackBy);
        body.put("ackRemark", ackRemark);
        body.put("requestId", requestId);
        return body;
    }

    private List<FishingReservation> reservationsOf(Long orderId) {
        return reservationMapper.selectList(new QueryWrapper<FishingReservation>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private List<FishingLoad> loadsOf(Long reservationId) {
        return loadMapper.selectList(new QueryWrapper<FishingLoad>()
                .eq("reservation_id", reservationId).orderByAsc("id"));
    }

    private FishingVariance varianceOf(Long reservationId) {
        return varianceMapper.selectOne(new QueryWrapper<FishingVariance>()
                .eq("reservation_id", reservationId).last("limit 1"));
    }

    private List<FishingNotification> notificationsOf(Long orderId) {
        return notificationMapper.selectList(new QueryWrapper<FishingNotification>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private long countEvidence(String action, Long entityId) {
        QueryWrapper<FishingChangeEvidence> wrapper = new QueryWrapper<FishingChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long count = evidenceMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

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
        sb.append("\n========== 捕捞预约与规格分仓验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("===============================================\n");
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
