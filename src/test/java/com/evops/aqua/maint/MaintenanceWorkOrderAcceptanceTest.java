package com.evops.aqua.maint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.entity.BizLock;
import com.evops.aqua.maint.entity.ConcurrentAudit;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.PartReplacement;
import com.evops.aqua.maint.entity.SafetyCheck;
import com.evops.aqua.maint.entity.SparePart;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.entity.WorkOrderPart;
import com.evops.aqua.maint.mapper.BizLockMapper;
import com.evops.aqua.maint.mapper.ConcurrentAuditMapper;
import com.evops.aqua.maint.mapper.MaintChangeEvidenceMapper;
import com.evops.aqua.maint.mapper.PartReplacementMapper;
import com.evops.aqua.maint.mapper.SafetyCheckMapper;
import com.evops.aqua.maint.mapper.SparePartMapper;
import com.evops.aqua.maint.mapper.WorkOrderMapper;
import com.evops.aqua.maint.mapper.WorkOrderPartMapper;
import com.evops.aqua.maint.service.ConcurrencyGuard;
import com.evops.aqua.maint.service.ConcurrentRejectedException;
import com.evops.aqua.maint.service.OrderEscalationService;
import com.evops.aqua.maint.service.OrderGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 养殖设备保养工单验收（aqua-12）。
 * 主线：增氧机/泵站/投饵机建档与初始装机 → 停机窗口/维修人员/备件序列号 →
 * 双轨计划（运行小时+日历）→ 到期扫描生成工单（关联窗口/备件/人员）→
 * 计划调整只影响未生成工单 → 派工资质校验 → 并发开工裁决（谁生效谁拒绝+审计）→
 * 换件保留旧件序列号 → 完工释放余料 → 未验收不可关闭 → 逐项安全确认 → 并发恢复裁决 →
 * 关闭恢复运行 → 逾期自动升级 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaintenanceWorkOrderAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WorkOrderMapper orderMapper;
    @Autowired
    private WorkOrderPartMapper orderPartMapper;
    @Autowired
    private SparePartMapper partMapper;
    @Autowired
    private PartReplacementMapper replacementMapper;
    @Autowired
    private SafetyCheckMapper safetyMapper;
    @Autowired
    private BizLockMapper bizLockMapper;
    @Autowired
    private ConcurrentAuditMapper auditMapper;
    @Autowired
    private MaintChangeEvidenceMapper evidenceMapper;
    @Autowired
    private OrderGenerationService generationService;
    @Autowired
    private OrderEscalationService escalationService;
    @Autowired
    private ConcurrencyGuard concurrencyGuard;
    @Autowired
    private Scheduler quartzScheduler;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long aeratorId;
    private Long pumpId;
    private Long feederId;
    private Long catImpeller;
    private Long catGasket;
    private Long catSeal;
    private Long catAuger;
    private Long planAerator;
    private Long planFeeder;
    private Long workerZhao;
    private Long workerSun;
    private Long w1;
    private Long w2;
    private Long w3;
    private Long w4;
    private Long hoursOrder;
    private Long calendarOrder;
    private Long pumpManualOrder;
    private Long feederManualOrder;

    private final LocalDateTime now0 = LocalDateTime.now();

    @Test
    void acceptanceMaintenanceWorkOrder() {
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

        checkpoint("CP1", "设备建档：增氧机、泵站、投饵机各一台，默认运行中；非法类型拒绝", () -> {
            JsonNode bad = json(call(HttpMethod.POST, "/api/aqua/maint/devices",
                    deviceBody("BAD-01", "非法设备", "UNKNOWN", "管理员赵海")));
            check(!bad.path("success").asBoolean(true), "非法设备类型应被拒绝");

            aeratorId = createDevice("AER-01", "一号增氧机", "AERATOR");
            pumpId = createDevice("PUMP-01", "中央泵站", "PUMP_STATION");
            feederId = createDevice("FEED-01", "三号投饵机", "FEEDER");
            JsonNode aerator = json(call(HttpMethod.GET, "/api/aqua/maint/devices/" + aeratorId, null));
            check("RUNNING".equals(aerator.path("data").path("status").asText()), "新设备应运行中");
            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/maint/devices",
                    deviceBody("AER-01", "重复增氧机", "AERATOR", "管理员赵海")));
            check(!dup.path("success").asBoolean(true), "重复设备编码应被拒绝");
        });

        checkpoint("CP2", "备件品类/序列号入库与初始装机：一物一序列号，重复序列号幂等", () -> {
            catImpeller = createCatalog("IMP", "增氧机叶轮", "φ300");
            catGasket = createCatalog("GSK", "密封垫圈", "DN50");
            catSeal = createCatalog("SEAL", "泵轴密封件", "MB-20");
            catAuger = createCatalog("AUG", "螺旋送料器", "L-800");

            // 增氧机：旧叶轮初始在装，新叶轮两个（小时单/日历单各锁定一个），垫圈两个
            check(stockPart(catImpeller, "IMP-OLD") != null, "旧叶轮入库");
            check(stockPart(catImpeller, "IMP-NEW") != null, "新叶轮A入库");
            check(stockPart(catImpeller, "IMP-NEW2") != null, "新叶轮B入库");
            check(stockPart(catImpeller, "IMP-NEW3") != null, "游离新叶轮入库");
            check(stockPart(catGasket, "GSK-1") != null, "垫圈1入库");
            check(stockPart(catGasket, "GSK-2") != null, "垫圈2入库");
            // 泵站：旧密封在装、新密封一个；投饵机：新送料器一个
            check(stockPart(catSeal, "SEAL-OLD") != null, "旧密封件入库");
            check(stockPart(catSeal, "SEAL-NEW") != null, "新密封件入库");
            check(stockPart(catAuger, "AUG-NEW") != null, "新送料器入库");

            long impNewFirst = stockPart(catImpeller, "IMP-NEW");
            long impNewSecond = stockPart(catImpeller, "IMP-NEW");
            check(impNewFirst == impNewSecond, "同序列号重复入库必须返回首次实例（幂等）");

            installInitial(aeratorId, "IMP-OLD", "管理员赵海");
            installInitial(pumpId, "SEAL-OLD", "管理员赵海");
            check("INSTALLED".equals(partBySerial("IMP-OLD").path("status").asText())
                            && partBySerial("IMP-OLD").path("installedDeviceId").asLong() == aeratorId,
                    "初始装机后旧叶轮应装在增氧机上");
        });

        checkpoint("CP3", "停机窗口：半开区间登记；重叠窗口拒绝；为三设备登记未来/过去窗口", () -> {
            // 增氧机两个未来开放窗口（小时单/日历单各一），端点相接允许
            LocalDateTime s1 = now0.plusHours(1), e1 = now0.plusHours(2);
            LocalDateTime s2 = now0.plusHours(3), e2 = now0.plusHours(4);
            w1 = registerWindow(aeratorId, s1, e1, "凌晨例行停机1", "win-aer-1");
            w2 = registerWindow(aeratorId, s2, e2, "凌晨例行停机2", "win-aer-2");
            // 与 W1 重叠（半开判定）必须拒绝
            JsonNode overlap = json(call(HttpMethod.POST,
                    "/api/aqua/maint/devices/" + aeratorId + "/windows",
                    windowBody(now0.plusMinutes(30), now0.plusMinutes(90), "重叠窗口", "win-aer-bad")));
            check(!overlap.path("success").asBoolean(true)
                            && overlap.path("message").asText().contains("重叠"),
                    "与开放窗口重叠的登记应被拒绝：" + overlap.path("message").asText());
            // 端点相接不算重叠——但必须与未来时间相接，这里用 W2 结束端点再开一个
            LocalDateTime s5 = e2, e5 = e2.plusHours(1);
            Long w5 = registerWindow(pumpId, s5, e5, "泵站未来备用窗口", "win-pump-future");
            check(w5 != null, "端点相接窗口应允许登记");

            // 泵站过去窗口（2小时前，用于逾期升级 WARNING 场景）；投饵机72小时前窗口（ESCALATED 场景）
            w3 = registerWindow(pumpId, now0.minusHours(2), now0.minusHours(1), "泵站抢修窗口", "win-pump-past");
            w4 = registerWindow(feederId, now0.minusHours(72), now0.minusHours(71), "投饵机检修窗口", "win-feed-past");
            // 同 requestId 重复登记返回同一窗口
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/maint/devices/" + aeratorId + "/windows",
                    windowBody(s1, e1, "凌晨例行停机1", "win-aer-1")));
            check(again.path("data").path("id").asLong() == w1, "窗口登记重复请求应幂等返回首次记录");
        });

        checkpoint("CP4", "维修人员：按设备类型登记资质；工号重复拒绝", () -> {
            workerZhao = createWorker("W01", "赵机电", "AERATOR,PUMP_STATION");
            workerSun = createWorker("W02", "孙投喂", "FEEDER");
            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/maint/workers",
                    workerBody("W01", "赵机电二号", "AERATOR")));
            check(!dup.path("success").asBoolean(true), "重复工号应被拒绝");
            check(workerZhao != null && workerSun != null, "两名维修人员应落库");
        });

        checkpoint("CP5", "保养计划：增氧机双轨（100运行小时/30日历天）、泵站小时轨、投饵机日历轨；双轨均关拒绝、一设备一计划", () -> {
            LocalDate anchorA = LocalDate.now().minusDays(40);
            JsonNode aerPlan = json(call(HttpMethod.POST, "/api/aqua/maint/plans",
                    planBody("PLAN-AER", aeratorId, "增氧机保养", true, 100, true, 30, anchorA,
                            listOf(partTpl(catImpeller, 1), partTpl(catGasket, 1)), "plan-aer")));
            assertSuccess(aerPlan, "增氧机双轨计划");
            planAerator = aerPlan.path("data").path("id").asLong();
            check(aerPlan.path("data").path("versionNo").asInt() == 1
                            && aerPlan.path("data").path("lastGeneratedReading").asInt() == 0,
                    "新计划版本应为v1、小时基准从当前读数0起算");

            JsonNode pumpPlan = json(call(HttpMethod.POST, "/api/aqua/maint/plans",
                    planBody("PLAN-PUMP", pumpId, "泵站保养", true, 500, false, null, null,
                            listOf(partTpl(catSeal, 1)), "plan-pump")));
            assertSuccess(pumpPlan, "泵站小时轨计划");

            // 此时投饵机尚无计划：两轨均关必须先被规则拦截（而不是被一设备一计划拦截）
            JsonNode noTrack = json(call(HttpMethod.POST, "/api/aqua/maint/plans",
                    planBody("PLAN-BAD", feederId, "无触发轨计划", false, null, false, null, null,
                            listOf(), "plan-bad")));
            check(!noTrack.path("success").asBoolean(true) && noTrack.path("message").asText().contains("至少"),
                    "两轨均关的计划应被拒绝：" + noTrack.path("message").asText());

            JsonNode feederPlan = json(call(HttpMethod.POST, "/api/aqua/maint/plans",
                    planBody("PLAN-FEED", feederId, "投饵机保养", false, null, true, 7,
                            LocalDate.now().minusDays(10), listOf(partTpl(catAuger, 1)), "plan-feed")));
            assertSuccess(feederPlan, "投饵机日历轨计划");
            planFeeder = feederPlan.path("data").path("id").asLong();

            JsonNode second = json(call(HttpMethod.POST, "/api/aqua/maint/plans",
                    planBody("PLAN-AER2", aeratorId, "增氧机第二计划", true, 200, false, null, null,
                            listOf(), "plan-aer2")));
            check(!second.path("success").asBoolean(true) && second.path("message").asText().contains("一个计划"),
                    "一设备只允许一个计划：" + second.path("message").asText());
        });

        checkpoint("CP6", "运行小时上报只追加、读数不得回退；到期扫描生成小时工单并冻结计划版本、锁定备件", () -> {
            JsonNode reading = json(call(HttpMethod.POST,
                    "/api/aqua/maint/devices/" + aeratorId + "/meter-readings",
                    readingBody(120, LocalDate.now(), "赵机电", "meter-1")));
            assertSuccess(reading, "上报累计120小时");
            check(reading.path("data").path("readingHours").asInt() == 120, "读数应落库120");
            JsonNode rollback = json(call(HttpMethod.POST,
                    "/api/aqua/maint/devices/" + aeratorId + "/meter-readings",
                    readingBody(50, LocalDate.now(), "赵机电", "meter-bad")));
            check(!rollback.path("success").asBoolean(true)
                            && rollback.path("message").asText().contains("回退"),
                    "运行小时回退应被拒绝：" + rollback.path("message").asText());
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/maint/devices/" + aeratorId + "/meter-readings",
                    readingBody(120, LocalDate.now(), "赵机电", "meter-1")));
            check(again.path("data").path("id").asLong() == reading.path("data").path("id").asLong(),
                    "同requestId重复上报必须返回首次记录");

            Map<String, Object> scan = generationService.scan(now0);
            check((Integer) scan.get("createdCount") == 2,
                    "首次扫描应生成2张工单（增氧机小时轨+日历轨），实际=" + scan.get("createdCount"));
            check((Integer) scan.get("skippedCount") >= 1,
                    "投饵机日历到期但无可用窗口应跳过，实际跳过=" + scan.get("skippedCount"));

            List<WorkOrder> aerOrders = orderMapper.selectList(new QueryWrapper<WorkOrder>()
                    .eq("device_id", aeratorId).orderByAsc("id"));
            check(aerOrders.size() == 2, "增氧机应有小时、日历两张工单，实际=" + aerOrders.size());
            WorkOrder hours = null;
            WorkOrder calendar = null;
            for (WorkOrder o : aerOrders) {
                if ("HOURS".equals(o.getTriggerSource())) {
                    hours = o;
                }
                if ("CALENDAR".equals(o.getTriggerSource())) {
                    calendar = o;
                }
            }
            hoursOrder = hours.getId();
            calendarOrder = calendar.getId();
            check("HOURS".equals(hours.getTriggerSource())
                            && hours.getDueReading().intValue() == 100
                            && hours.getDueToken().startsWith("H100")
                            && hours.getPlanVersionNo() == 1
                            && hours.getDowntimeWindowId().equals(w1),
                    "小时工单应冻结到期读数100、版本v1并关联窗口W1，实际=" + hours.getFrozenDetail());
            check("CALENDAR".equals(calendar.getTriggerSource())
                            && calendar.getDowntimeWindowId().equals(w2)
                            && calendar.getPlanVersionNo() == 1,
                    "日历工单应关联窗口W2并冻结v1");
            check(hours.getFrozenDetail().contains("v1"), "工单应冻结计划版本快照");

            // 备件需求行复制模板并锁定实物
            List<WorkOrderPart> lines = orderPartMapper.selectList(
                    new QueryWrapper<WorkOrderPart>().eq("work_order_id", hoursOrder));
            check(lines.size() == 2 && lines.stream().allMatch(l -> "RESERVED".equals(l.getStatus())),
                    "小时工单应有两行备件且均已锁定");
            long reservedImp = partMapper.selectCount(new QueryWrapper<SparePart>()
                    .eq("serial_no", "IMP-NEW").eq("status", "RESERVED")
                    .eq("reserved_work_order_id", hoursOrder));
            check(reservedImp == 1, "新叶轮A应被小时工单锁定");
        });

        checkpoint("CP7", "重扫幂等：同轨有未结单不重复生成；无窗口跳过留证且只留首次", () -> {
            Map<String, Object> rerun = generationService.scan(now0);
            check((Integer) rerun.get("createdCount") == 0,
                    "重复扫描不得再生成工单，实际新建=" + rerun.get("createdCount"));
            check(orderMapper.selectCount(new QueryWrapper<WorkOrder>()) == 2L,
                    "工单总数应仍为2");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skipped = (List<Map<String, Object>>) rerun.get("skipped");
            boolean feederNoWindow = skipped.stream().anyMatch(s ->
                    String.valueOf(s.get("reason")).contains("没有可预约的开放停机窗口"));
            check(feederNoWindow, "投饵机无窗口的跳过原因应明确给出");
            long skipEvidence = evidenceMapper.selectCount(new QueryWrapper<MaintChangeEvidence>()
                    .eq("action", MaintChangeEvidence.ACTION_GENERATE_SKIP)
                    .eq("entity_id", planFeeder));
            generationService.scan(now0);
            long skipEvidenceAfter = evidenceMapper.selectCount(new QueryWrapper<MaintChangeEvidence>()
                    .eq("action", MaintChangeEvidence.ACTION_GENERATE_SKIP)
                    .eq("entity_id", planFeeder));
            check(skipEvidence >= 1 && skipEvidence == skipEvidenceAfter,
                    "跳过必须留证且周期重扫不重复刷证据：" + skipEvidence + " -> " + skipEvidenceAfter);
        });

        checkpoint("CP8", "计划调整只影响未生成工单：间隔改200小时版本升v2，已生成两张工单仍冻结v1不回改", () -> {
            Map<String, Object> adj = new HashMap<>();
            adj.put("intervalHours", 200);
            adj.put("operator", "设备主管周工");
            adj.put("reason", "夏季高负荷延长保养间隔");
            adj.put("requestId", "plan-adjust-1");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/maint/plans/" + planAerator + "/adjust", adj));
            assertSuccess(resp, "调整增氧机计划");
            check(resp.path("data").path("versionNo").asInt() == 2, "调整后版本应为v2");

            WorkOrder hours = orderMapper.selectById(hoursOrder);
            WorkOrder calendar = orderMapper.selectById(calendarOrder);
            check(hours.getPlanVersionNo() == 1 && calendar.getPlanVersionNo() == 1
                            && hours.getDueReading().intValue() == 100,
                    "已生成工单必须保留旧版本v1与旧到期读数，不受调整影响");
            // 当前读数120，新基准100，新间隔200：差值20<200，不应再生成小时工单
            Map<String, Object> scan = generationService.scan(now0);
            check(orderMapper.selectCount(new QueryWrapper<WorkOrder>()
                    .eq("plan_id", planAerator).eq("trigger_source", "HOURS")) == 1L
                            && (Integer) scan.get("createdCount") == 0,
                    "按新间隔未到期，调整不追溯补单");
            // 调整幂等
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/maint/plans/" + planAerator + "/adjust", adj));
            check(again.path("data").path("versionNo").asInt() == 2, "同requestId重复调整不得再升版本");
        });

        checkpoint("CP9", "临时工单关联窗口/备件：窗口不属于设备拒绝、库存不足整单失败不留半锁定、窗口唯一占用、requestId幂等", () -> {
            // 窗口不属于设备：给增氧机开单却用泵站窗口
            JsonNode wrongDevice = json(call(HttpMethod.POST, "/api/aqua/maint/orders/manual",
                    manualBody(aeratorId, w3, now0.minusHours(2), now0.minusHours(1),
                            listOf(partLine(catImpeller, 1)), "抢修增氧机", "manual-wrong")));
            check(!wrongDevice.path("success").asBoolean(true)
                            && wrongDevice.path("message").asText().contains("不属于"),
                    "关联他设备窗口应被拒绝：" + wrongDevice.path("message").asText());

            // 投饵机窗口W4：先要5个送料器而库存仅1 → 整单失败
            JsonNode noStock = json(call(HttpMethod.POST, "/api/aqua/maint/orders/manual",
                    manualBody(feederId, w4, now0.minusHours(72), now0.minusHours(71),
                            listOf(partLine(catAuger, 5)), "投饵机大修", "manual-feed-fail")));
            check(!noStock.path("success").asBoolean(true)
                            && noStock.path("message").asText().contains("不足"),
                    "备件库存不足应拒绝：" + noStock.path("message").asText());
            check("IN_STOCK".equals(partBySerial("AUG-NEW").path("status").asText()),
                    "失败工单不得残留半占用，AUG-NEW应仍在库");
            check(orderMapper.selectCount(new QueryWrapper<WorkOrder>().eq("downtime_window_id", w4)) == 0L,
                    "失败工单不得占用窗口W4");

            // 泵站临时工单（W4 现在仍空闲给投饵机；W3 给泵站）
            JsonNode pumpOrder = json(call(HttpMethod.POST, "/api/aqua/maint/orders/manual",
                    manualBody(pumpId, w3, now0.minusHours(2), now0.minusHours(1),
                            listOf(partLine(catSeal, 1)), "泵站密封抢修", "manual-pump-1")));
            assertSuccess(pumpOrder, "泵站临时工单");
            pumpManualOrder = pumpOrder.path("data").path("id").asLong();
            check("MANUAL".equals(pumpOrder.path("data").path("triggerSource").asText()), "来源应为MANUAL");
            check("SEAL-NEW".equals(firstReservedSerial(pumpManualOrder, catSeal)),
                    "泵站工单应锁定SEAL-NEW，实际锁定=" + firstReservedSerial(pumpManualOrder, catSeal));

            JsonNode pumpAgain = json(call(HttpMethod.POST, "/api/aqua/maint/orders/manual",
                    manualBody(pumpId, w3, now0.minusHours(2), now0.minusHours(1),
                            listOf(partLine(catSeal, 1)), "泵站密封抢修", "manual-pump-1")));
            check(pumpAgain.path("data").path("id").asLong() == pumpManualOrder,
                    "同requestId重复开票必须返回首次工单");
            // 另一请求占用已用窗口 W3 → 拒绝
            JsonNode windowUsed = json(call(HttpMethod.POST, "/api/aqua/maint/orders/manual",
                    manualBody(pumpId, w3, now0.minusHours(2), now0.minusHours(1),
                            listOf(partLine(catSeal, 1)), "再次占用窗口", "manual-pump-2")));
            check(!windowUsed.path("success").asBoolean(true),
                    "一张停机窗口只能安排一张工单，重复占用应被拒绝");

            // 投饵机正式逾期工单（W4，1个送料器，库存足够）
            JsonNode feederOrder = json(call(HttpMethod.POST, "/api/aqua/maint/orders/manual",
                    manualBody(feederId, w4, now0.minusHours(72), now0.minusHours(71),
                            listOf(partLine(catAuger, 1)), "投饵机检修", "manual-feed-1")));
            assertSuccess(feederOrder, "投饵机临时工单");
            feederManualOrder = feederOrder.path("data").path("id").asLong();
        });

        checkpoint("CP10", "派工关联维修人员：无资质人员拒绝；合格派工成功并设主修；重复派工拒绝", () -> {
            Map<String, Object> badAssign = new HashMap<>();
            badAssign.put("workerIds", listOf(workerSun));
            badAssign.put("assignedBy", "班长郑工");
            JsonNode rejected = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/assign", badAssign));
            check(!rejected.path("success").asBoolean(true)
                            && rejected.path("message").asText().contains("资质"),
                    "投饵机资质人员派给增氧机工单应被拒绝并说明资质不符："
                            + rejected.path("message").asText());

            Map<String, Object> assign = new HashMap<>();
            assign.put("workerIds", listOf(workerZhao));
            assign.put("assignedBy", "班长郑工");
            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/assign", assign));
            assertSuccess(ok, "赵机电派工到小时工单");
            check("ASSIGNED".equals(ok.path("data").path("status").asText())
                            && ok.path("data").path("leadWorkerId").asLong() == workerZhao,
                    "工单应转已派工并记录主修人员");
            JsonNode workers = json(call(HttpMethod.GET,
                    "/api/aqua/maint/orders/" + hoursOrder + "/workers", null));
            check(workers.path("data").size() == 1
                            && "赵机电".equals(workers.path("data").get(0).path("workerName").asText())
                            && "班长郑工".equals(workers.path("data").get(0).path("assignedBy").asText()),
                    "工单必须关联维修人员并留派工人");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/assign", assign));
            check(!again.path("success").asBoolean(true), "已派工单重复派工应被拒绝");
        });

        checkpoint("CP11", "并发开工裁决：先到者生效、后到者拒绝并说明谁生效，双方均留审计；开工即停机并生成安全清单", () -> {
            // 确定性模拟：持锁中的生效请求 start-win，并发请求 start-lose 必须被拒绝
            insertLock("WORK_ORDER_START:" + hoursOrder, "start-win", "抢先电工");
            JsonNode loser = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/start",
                    startBody("钱维修", "start-lose")));
            check(!loser.path("success").asBoolean(true), "并发中的后到开工请求应被拒绝");
            check(loser.path("message").asText().contains("start-win")
                            && loser.path("message").asText().contains("抢先电工")
                            && loser.path("message").asText().contains("钱维修"),
                    "拒绝响应必须说明谁生效（start-win/抢先电工）谁被拒（钱维修）："
                            + loser.path("message").asText());
            ConcurrentAudit rejectedAudit = auditMapper.selectOne(new QueryWrapper<ConcurrentAudit>()
                    .eq("request_id", "start-lose"));
            check(rejectedAudit != null
                            && ConcurrentAudit.VERDICT_REJECTED.equals(rejectedAudit.getVerdict())
                            && "start-win".equals(rejectedAudit.getEffectiveRequestId())
                            && "抢先电工".equals(rejectedAudit.getEffectiveOperator())
                            && rejectedAudit.getRejectReason().contains("并发被拒"),
                    "被拒请求必须留 CONCURRENT_REJECTED 审计并指认生效请求");
            clearLock("WORK_ORDER_START:" + hoursOrder);

            // 持锁释放后，正式开工生效
            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/start",
                    startBody("赵机电", "start-ok")));
            assertSuccess(ok, "正式开工");
            check("IN_PROGRESS".equals(ok.path("data").path("status").asText()), "工单应转进行中");
            check("DOWN".equals(json(call(HttpMethod.GET,
                    "/api/aqua/maint/devices/" + aeratorId, null)).path("data").path("status").asText()),
                    "开工后增氧机必须停机(DOWN)");
            List<SafetyCheck> checks = safetyMapper.selectList(
                    new QueryWrapper<SafetyCheck>().eq("work_order_id", hoursOrder).orderByAsc("id"));
            check(checks.size() == 6
                            && checks.stream().allMatch(c -> SafetyCheck.STATUS_PENDING.equals(c.getStatus())),
                    "增氧机开工应生成6项待确认安全清单（3通用+3增氧机专项），实际=" + checks.size());
            ConcurrentAudit effectiveAudit = auditMapper.selectOne(new QueryWrapper<ConcurrentAudit>()
                    .eq("request_id", "start-ok"));
            check(effectiveAudit != null
                            && ConcurrentAudit.VERDICT_EFFECTIVE.equals(effectiveAudit.getVerdict())
                            && "赵机电".equals(effectiveAudit.getOperator()),
                    "生效请求必须留 EFFECTIVE 审计");
            // 同 requestId 重放幂等
            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/start",
                    startBody("赵机电", "start-ok")));
            check(replay.path("data").path("id").asLong() == hoursOrder
                            && "IN_PROGRESS".equals(replay.path("data").path("status").asText()),
                    "开工请求重放应返回同一工单");

            // 真实双线程同刻竞争：屏障处同时放行，谁先拿到锁谁生效（不预设输赢），
            // 生效者持锁2秒，被拒者必须立即失败并指认生效者。
            String raceKey = "CONCURRENCY_PROBE:RACE-1";
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<String> effectiveRequest = new AtomicReference<>();
            AtomicReference<String> effectiveOperator = new AtomicReference<>();
            List<ConcurrentRejectedException> loserExceptions = new ArrayList<>();
            AtomicInteger effectiveCount = new AtomicInteger();
            Runnable racerA = racer(barrier, raceKey, "race-A", "操作者甲",
                    effectiveCount, effectiveRequest, effectiveOperator, loserExceptions);
            Runnable racerB = racer(barrier, raceKey, "race-B", "操作者乙",
                    effectiveCount, effectiveRequest, effectiveOperator, loserExceptions);
            Thread t1 = new Thread(racerA, "race-A");
            Thread t2 = new Thread(racerB, "race-B");
            t1.start();
            t2.start();
            t1.join(15000);
            t2.join(15000);
            check(effectiveCount.get() == 1,
                    "同一操作真实并发只能有1个生效，实际=" + effectiveCount.get());
            check(loserExceptions.size() == 1,
                    "真实并发必须恰有1个被拒，实际=" + loserExceptions.size());
            check(effectiveRequest.get() != null
                            && loserExceptions.get(0).getMessage().contains(effectiveRequest.get())
                            && loserExceptions.get(0).getMessage().contains(effectiveOperator.get()),
                    "被拒响应必须指认生效请求号与操作者，实际=" + loserExceptions.get(0).getMessage());
            String loserRequest = "race-A".equals(effectiveRequest.get()) ? "race-B" : "race-A";
            ConcurrentAudit raceRejected = auditMapper.selectOne(new QueryWrapper<ConcurrentAudit>()
                    .eq("request_id", loserRequest));
            check(raceRejected != null
                            && ConcurrentAudit.VERDICT_REJECTED.equals(raceRejected.getVerdict())
                            && effectiveRequest.get().equals(raceRejected.getEffectiveRequestId()),
                    "真实并发被拒方必须留审计指认生效请求");
        });

        checkpoint("CP12", "备件替换保留旧件序列号：新件非本单锁定拒绝、旧件不存在拒绝、旧件状态与装机信息保留", () -> {
            // 游离新件（未被本单锁定）拒绝
            JsonNode notReserved = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/parts/" + catImpeller + "/replace",
                    replaceBody("IMP-NEW3", "IMP-OLD", "赵机电", "rep-bad-1")));
            check(!notReserved.path("success").asBoolean(true)
                            && notReserved.path("message").asText().contains("锁定"),
                    "非本单锁定备件装机应被拒绝：" + notReserved.path("message").asText());
            // 旧件序列号不存在拒绝
            JsonNode oldMissing = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/parts/" + catImpeller + "/replace",
                    replaceBody("IMP-NEW", "IMP-GHOST", "赵机电", "rep-bad-2")));
            check(!oldMissing.path("success").asBoolean(true)
                            && oldMissing.path("message").asText().contains("旧件序列号"),
                    "旧件序列号不在台账必须拒绝并提示留存要求：" + oldMissing.path("message").asText());

            // 正式换件：新件 IMP-NEW 装机，旧件 IMP-OLD 报废留存
            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/parts/" + catImpeller + "/replace",
                    replaceBody("IMP-NEW", "IMP-OLD", "赵机电", "rep-1")));
            assertSuccess(ok, "叶轮换件");
            SparePart oldPart = partMapper.selectOne(
                    new QueryWrapper<SparePart>().eq("serial_no", "IMP-OLD"));
            SparePart newPart = partMapper.selectOne(
                    new QueryWrapper<SparePart>().eq("serial_no", "IMP-NEW"));
            check(SparePart.STATUS_SCRAPPED.equals(oldPart.getStatus())
                            && oldPart.getRemovedWorkOrderId().equals(hoursOrder)
                            && oldPart.getRemovedAt() != null
                            && oldPart.getInstalledDeviceId().equals(aeratorId)
                            && oldPart.getInstalledAt() != null,
                    "旧件必须转SCRAPPED且序列号、原装机信息、拆除工单全部保留");
            check(SparePart.STATUS_INSTALLED.equals(newPart.getStatus())
                            && newPart.getInstalledDeviceId().equals(aeratorId)
                            && newPart.getInstalledWorkOrderId().equals(hoursOrder),
                    "新件必须装机到增氧机并记录装机工单");
            PartReplacement row = replacementMapper.selectOne(
                    new QueryWrapper<PartReplacement>().eq("request_id", "rep-1"));
            check(row != null
                            && "IMP-NEW".equals(row.getNewSerialNo())
                            && "IMP-OLD".equals(row.getRemovedSerialNo())
                            && "赵机电".equals(row.getReplacedBy()),
                    "换件记录必须同时留存新旧序列号与换件人");
            // 同 requestId 重试不多换
            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/parts/" + catImpeller + "/replace",
                    replaceBody("IMP-NEW", "IMP-OLD", "赵机电", "rep-1")));
            assertSuccess(replay, "换件重试");
            check(replacementMapper.selectCount(new QueryWrapper<PartReplacement>()
                    .eq("request_id", "rep-1")) == 1L, "重复换件请求不得多写记录");
        });

        checkpoint("CP13", "完工释放未用备件；非进行中工单不能完工", () -> {
            JsonNode badState = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + calendarOrder + "/complete?operator=赵机电", null));
            check(!badState.path("success").asBoolean(true), "待派工单完工应被拒绝");

            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/complete?operator=赵机电", null));
            assertSuccess(ok, "小时工单完工");
            check("COMPLETED".equals(ok.path("data").path("status").asText())
                            && ok.path("data").path("completedAt") != null,
                    "工单应转完工待验收并记录完工时间");
            SparePart gasket = partMapper.selectOne(
                    new QueryWrapper<SparePart>().eq("serial_no", "GSK-1"));
            check(SparePart.STATUS_IN_STOCK.equals(gasket.getStatus())
                            && gasket.getReservedWorkOrderId() == null,
                    "完工后未使用的锁定垫圈必须释放回在库");
            // 日历工单的垫圈仍应处于锁定
            check(SparePart.STATUS_RESERVED.equals(partMapper.selectOne(
                    new QueryWrapper<SparePart>().eq("serial_no", "GSK-2")).getStatus()),
                    "未完工工单的锁定备件不得被释放");
        });

        checkpoint("CP14", "未验收不能关闭；验收人与意见缺一不可；验收通过落验收人", () -> {
            JsonNode closeBeforeAccept = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/close",
                    closeBody("孙班长", "close-too-soon")));
            check(!closeBeforeAccept.path("success").asBoolean(true)
                            && closeBeforeAccept.path("message").asText().contains("未验收"),
                    "未验收工单关闭必须被拒绝并说明：" + closeBeforeAccept.path("message").asText());

            JsonNode acceptPlanned = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + calendarOrder + "/accept",
                    acceptBody("孙班长", "日历单还没修，验收应被拒绝")));
            check(!acceptPlanned.path("success").asBoolean(true), "非完工待验收工单不能验收");

            JsonNode noAcceptor = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/accept",
                    acceptBody(null, "漏填验收人")));
            check(!noAcceptor.path("success").asBoolean(true), "缺验收人必须被参数校验拒绝");

            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/accept",
                    acceptBody("质检冯工", "叶轮运转平稳，振动温度正常，同意通过")));
            assertSuccess(ok, "验收通过");
            check("ACCEPTED".equals(ok.path("data").path("status").asText())
                            && "质检冯工".equals(ok.path("data").path("acceptedBy").asText())
                            && ok.path("data").path("acceptedAt") != null,
                    "验收人与验收时间必须落库");
        });

        checkpoint("CP15", "恢复运行前必须完成安全确认；并发恢复一个生效一个被拒；关闭后设备恢复运行", () -> {
            // 已验收但安全项未确认：关闭拒绝并逐项列出
            JsonNode unsafe = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/close",
                    closeBody("孙班长", "close-unsafe")));
            check(!unsafe.path("success").asBoolean(true)
                            && unsafe.path("message").asText().contains("安全确认"),
                    "安全确认未全部完成不得恢复运行：" + unsafe.path("message").asText());
            check(unsafe.path("message").asText().contains("电气绝缘"),
                    "拒绝信息应列出未确认项：" + unsafe.path("message").asText());

            // 逐项确认全部6项
            JsonNode checksNode = json(call(HttpMethod.GET,
                    "/api/aqua/maint/orders/" + hoursOrder + "/safety-checks", null));
            check(checksNode.path("data").size() == 6, "应能查到6项安全确认");
            for (JsonNode item : checksNode.path("data")) {
                JsonNode c = json(call(HttpMethod.POST, "/api/aqua/maint/orders/" + hoursOrder
                                + "/safety-checks/" + item.path("id").asLong() + "/confirm",
                        safetyBody("安全员郭工", "已现场复核")));
                assertSuccess(c, "确认安全项" + item.path("itemCode").asText());
            }
            // 重复确认幂等，不覆盖首次确认人
            JsonNode firstItem = checksNode.path("data").get(0);
            JsonNode reconfirm = json(call(HttpMethod.POST, "/api/aqua/maint/orders/" + hoursOrder
                            + "/safety-checks/" + firstItem.path("id").asLong() + "/confirm",
                    safetyBody("其他人", "重复确认")));
            check("安全员郭工".equals(reconfirm.path("data").path("confirmedBy").asText()),
                    "重复安全确认不得覆盖首次确认人");

            // 并发恢复裁决：持锁生效方 resume-win，本请求被拒
            insertLock("DEVICE_RESUME:" + aeratorId, "resume-win", "抢先班长");
            JsonNode loser = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/close",
                    closeBody("钱维修", "resume-lose")));
            check(!loser.path("success").asBoolean(true), "并发恢复的后到请求应被拒绝");
            check(loser.path("message").asText().contains("resume-win")
                            && loser.path("message").asText().contains("抢先班长"),
                    "拒绝响应必须指认生效请求：" + loser.path("message").asText());
            ConcurrentAudit rejected = auditMapper.selectOne(new QueryWrapper<ConcurrentAudit>()
                    .eq("request_id", "resume-lose"));
            check(rejected != null && "resume-win".equals(rejected.getEffectiveRequestId()),
                    "被拒恢复请求必须留审计并指认生效请求");
            clearLock("DEVICE_RESUME:" + aeratorId);

            // 正式关闭恢复运行
            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/close",
                    closeBody("孙班长", "resume-ok")));
            assertSuccess(ok, "关闭工单");
            check("CLOSED".equals(ok.path("data").path("status").asText())
                            && ok.path("data").path("closedAt") != null,
                    "工单应关闭并记录关闭时间");
            check("RUNNING".equals(json(call(HttpMethod.GET,
                    "/api/aqua/maint/devices/" + aeratorId, null)).path("data").path("status").asText()),
                    "关闭后增氧机必须恢复运行");
            ConcurrentAudit effective = auditMapper.selectOne(new QueryWrapper<ConcurrentAudit>()
                    .eq("request_id", "resume-ok"));
            check(effective != null && ConcurrentAudit.VERDICT_EFFECTIVE.equals(effective.getVerdict()),
                    "生效恢复请求必须留 EFFECTIVE 审计");
            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/maint/orders/" + hoursOrder + "/close",
                    closeBody("孙班长", "resume-ok")));
            check(replay.path("data").path("id").asLong() == hoursOrder
                            && "CLOSED".equals(replay.path("data").path("status").asText()),
                    "关闭请求重放应幂等返回已关闭工单");
        });

        checkpoint("CP16", "逾期自动升级：逾期2小时升WARNING、逾期72小时升ESCALATED；重扫不重复升级；Quartz任务已注册", () -> {
            check(quartzScheduler.getJobDetail(JobKey.jobKey("maintOrderGenerateScanJob", "aqua-maint")) != null
                            && quartzScheduler.getJobDetail(JobKey.jobKey("maintOrderEscalateScanJob", "aqua-maint")) != null,
                    "生成与逾期升级两个定时任务应注册到调度器");

            // 泵站临时工单逾期约2小时（未超24小时宽限）→ WARNING；投饵机逾期约72小时 → ESCALATED
            check("NORMAL".equals(orderMapper.selectById(pumpManualOrder).getPriority()),
                    "升级前泵站工单应为NORMAL");
            Map<String, Object> scan = escalationService.scan(now0);
            check((Integer) scan.get("escalatedCount") == 2,
                    "首次逾期扫描应升级2张工单，实际=" + scan.get("escalatedCount"));
            WorkOrder pump = orderMapper.selectById(pumpManualOrder);
            WorkOrder feeder = orderMapper.selectById(feederManualOrder);
            check(WorkOrder.PRIORITY_WARNING.equals(pump.getPriority())
                            && pump.getEscalateCount() == 1 && pump.getEscalatedAt() != null,
                    "逾期2小时应升WARNING并记升级次数，实际=" + pump.getPriority());
            check(WorkOrder.PRIORITY_ESCALATED.equals(feeder.getPriority())
                            && feeder.getEscalateCount() == 1,
                    "逾期72小时应直接升ESCALATED，实际=" + feeder.getPriority());

            // 重扫幂等：升级只追加不回落，不多写证据
            long evidenceBefore = evidenceMapper.selectCount(new QueryWrapper<MaintChangeEvidence>()
                    .eq("action", MaintChangeEvidence.ACTION_ESCALATE));
            Map<String, Object> rerun = escalationService.scan(now0);
            long evidenceAfter = evidenceMapper.selectCount(new QueryWrapper<MaintChangeEvidence>()
                    .eq("action", MaintChangeEvidence.ACTION_ESCALATE));
            check((Integer) rerun.get("escalatedCount") == 0 && evidenceBefore == evidenceAfter,
                    "重复扫描不得重复升级或重复留证，证据数 " + evidenceBefore + " -> " + evidenceAfter);
            check(WorkOrder.PRIORITY_WARNING.equals(orderMapper.selectById(pumpManualOrder).getPriority())
                            && WorkOrder.PRIORITY_ESCALATED.equals(orderMapper.selectById(feederManualOrder).getPriority()),
                    "升级档位应保持不回落");
        });

        checkpoint("CP17", "全程留证与并发审计：各类动作证据齐全且带操作者，旧件序列号可追溯，审计可按业务键/请求号查询", () -> {
            check(actionCount(MaintChangeEvidence.ACTION_REGISTER_DEVICE, null) == 3, "设备登记证据应为3条");
            check(actionCount(MaintChangeEvidence.ACTION_CREATE_PLAN, null) == 3, "计划创建证据应为3条");
            check(actionCount(MaintChangeEvidence.ACTION_GENERATE_ORDER, null) == 2, "计划生成工单证据应为2条");
            check(actionCount(MaintChangeEvidence.ACTION_GENERATE_SKIP, null) >= 1, "跳过留证缺失");
            check(actionCount(MaintChangeEvidence.ACTION_ADJUST_PLAN, null) == 1, "计划调整证据应为1条");
            check(actionCount(MaintChangeEvidence.ACTION_ASSIGN, hoursOrder) == 1, "派工证据缺失");
            check(actionCount(MaintChangeEvidence.ACTION_START, hoursOrder) == 1, "开工证据缺失");
            check(actionCount(MaintChangeEvidence.ACTION_REPLACE_PART, null) == 1, "换件证据缺失");
            check(actionCount(MaintChangeEvidence.ACTION_COMPLETE, hoursOrder) == 1, "完工证据缺失");
            check(actionCount(MaintChangeEvidence.ACTION_ACCEPT, hoursOrder) == 1, "验收证据缺失");
            check(actionCount(MaintChangeEvidence.ACTION_SAFETY_CONFIRM, null) == 6, "安全确认证据应为6条");
            check(actionCount(MaintChangeEvidence.ACTION_CLOSE, hoursOrder) == 1, "关闭证据缺失");
            check(actionCount(MaintChangeEvidence.ACTION_ESCALATE, null) == 2, "逾期升级证据应为2条");
            check(actionCount(MaintChangeEvidence.ACTION_INSTALL_INITIAL, null) == 2, "初始装机证据应为2条");

            List<MaintChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            check(!all.isEmpty() && all.stream().allMatch(e -> e.getOperator() != null
                            && !e.getOperator().trim().isEmpty()),
                    "每条证据都必须带操作者（含定时任务的system-scheduler），不得丢操作者");
            boolean schedulerOperator = all.stream().anyMatch(e ->
                    OrderGenerationService.SCHEDULER_OPERATOR.equals(e.getOperator()));
            check(schedulerOperator, "定时生成/升级证据操作者应为system-scheduler");

            // 并发审计：两张临时工单+开工+恢复+真实并发探测共5条生效、3条被拒，且被拒行可双向指认
            long effective = auditMapper.selectCount(new QueryWrapper<ConcurrentAudit>()
                    .eq("verdict", ConcurrentAudit.VERDICT_EFFECTIVE));
            long rejected = auditMapper.selectCount(new QueryWrapper<ConcurrentAudit>()
                    .eq("verdict", ConcurrentAudit.VERDICT_REJECTED));
            check(effective == 5 && rejected == 3,
                    "并发审计应有5条生效（2张临时工单+开工+恢复+并发探测）、3条被拒，实际生效="
                            + effective + " 被拒=" + rejected);

            JsonNode auditByKey = json(call(HttpMethod.GET,
                    "/api/aqua/maint/concurrent-audits?bizKey=DEVICE_RESUME:" + aeratorId, null));
            assertSuccess(auditByKey, "按业务键查并发审计");
            check(auditByKey.path("data").size() == 2, "设备恢复业务键应查到一生效一被拒两条审计");
            JsonNode auditByRequest = json(call(HttpMethod.GET,
                    "/api/aqua/maint/concurrent-audits?requestId=start-lose", null));
            check(auditByRequest.path("data").size() == 1
                            && "start-win".equals(auditByRequest.path("data").get(0)
                                    .path("effectiveRequestId").asText()),
                    "按被拒请求号应查到其指认的生效请求号");

            JsonNode evidenceByOrder = json(call(HttpMethod.GET,
                    "/api/aqua/maint/evidence?entityType=WORK_ORDER&entityId=" + hoursOrder, null));
            assertSuccess(evidenceByOrder, "按工单查证据链");
            check(evidenceByOrder.path("data").size() == 6,
                    "小时工单证据链应含生成/派工/开工/完工/验收/关闭6条，实际="
                            + evidenceByOrder.path("data").size());

            // 旧件序列号永久可追溯：SCRAPPED 旧件 + 换件记录 + 新件在装
            JsonNode parts = json(call(HttpMethod.GET,
                    "/api/aqua/maint/spare-parts?catalogId=" + catImpeller, null));
            boolean oldKept = false;
            boolean newInstalled = false;
            for (JsonNode p : parts.path("data")) {
                if ("IMP-OLD".equals(p.path("serialNo").asText())
                        && "SCRAPPED".equals(p.path("status").asText())
                        && p.path("removedWorkOrderId").asLong() == hoursOrder) {
                    oldKept = true;
                }
                if ("IMP-NEW".equals(p.path("serialNo").asText())
                        && "INSTALLED".equals(p.path("status").asText())
                        && p.path("installedDeviceId").asLong() == aeratorId) {
                    newInstalled = true;
                }
            }
            check(oldKept && newInstalled, "旧件IMP-OLD报废留存、新件IMP-NEW在装，序列号链路必须可追溯");
        });

        report.add("验收结论：全部检查点通过——增氧机/泵站/投饵机双轨保养计划、停机窗口/备件/维修人员关联、计划调整只影响未生成工单、未验收不可关闭、备件替换保留旧件序列号、恢复运行前安全确认、逾期自动升级、同一操作并发裁决谁生效谁拒绝并保留审计");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Runnable racer(CyclicBarrier barrier, String raceKey,
                           String requestId, String operator,
                           AtomicInteger effectiveCount,
                           AtomicReference<String> effectiveRequest,
                           AtomicReference<String> effectiveOperator,
                           List<ConcurrentRejectedException> loserExceptions) {
        return () -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                ConcurrencyGuard.LeasedLock lease;
                try {
                    lease = concurrencyGuard.tryAcquire(raceKey, "RACE_PROBE", requestId, operator);
                } catch (ConcurrentRejectedException ex) {
                    synchronized (loserExceptions) {
                        loserExceptions.add(ex);
                    }
                    return;
                }
                effectiveCount.incrementAndGet();
                effectiveRequest.set(requestId);
                effectiveOperator.set(operator);
                concurrencyGuard.recordEffective(lease, "RACE_PROBE");
                Thread.sleep(2000); // 生效者持锁至被拒者完成裁决
                concurrencyGuard.release(lease);
            } catch (ConcurrentRejectedException ex) {
                synchronized (loserExceptions) {
                    loserExceptions.add(ex);
                }
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private long actionCount(String action, Long entityId) {
        QueryWrapper<MaintChangeEvidence> wrapper = new QueryWrapper<MaintChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long c = evidenceMapper.selectCount(wrapper);
        return c == null ? 0L : c;
    }

    private void insertLock(String key, String requestId, String operator) {
        BizLock lock = new BizLock();
        lock.setLockKey(key);
        lock.setRequestId(requestId);
        lock.setOperator(operator);
        lock.setLockedAt(LocalDateTime.now());
        bizLockMapper.insert(lock);
    }

    private void clearLock(String key) {
        bizLockMapper.delete(new QueryWrapper<BizLock>().eq("lock_key", key));
    }

    private String firstReservedSerial(Long orderId, Long catalogId) {
        SparePart part = partMapper.selectOne(new QueryWrapper<SparePart>()
                .eq("catalog_id", catalogId)
                .eq("status", "RESERVED")
                .eq("reserved_work_order_id", orderId)
                .orderByAsc("id").last("LIMIT 1"));
        return part == null ? null : part.getSerialNo();
    }

    private JsonNode partBySerial(String serial) {
        return objectMapper.valueToTree(partMapper.selectOne(
                new QueryWrapper<SparePart>().eq("serial_no", serial)));
    }

    private Long createDevice(String code, String name, String type) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/maint/devices",
                deviceBody(code, name, type, "管理员赵海")));
        assertSuccess(resp, "建档[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long createCatalog(String code, String name, String spec) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("partCode", code);
        body.put("partName", name);
        body.put("spec", spec);
        body.put("safetyStock", 1);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/maint/spare-catalogs", body));
        assertSuccess(resp, "备件品类[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockPart(Long catalogId, String serial) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("catalogId", catalogId);
        body.put("serialNo", serial);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/maint/spare-parts", body));
        assertSuccess(resp, "入库[" + serial + "]");
        return resp.path("data").path("id").asLong();
    }

    private void installInitial(Long deviceId, String serial, String operator) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("serialNo", serial);
        body.put("operator", operator);
        JsonNode resp = json(call(HttpMethod.POST,
                "/api/aqua/maint/devices/" + deviceId + "/installed-parts", body));
        assertSuccess(resp, "初始装机[" + serial + "]");
    }

    private Long registerWindow(Long deviceId, LocalDateTime start, LocalDateTime end,
                                String reason, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST,
                "/api/aqua/maint/devices/" + deviceId + "/windows",
                windowBody(start, end, reason, requestId)));
        assertSuccess(resp, "停机窗口[" + requestId + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long createWorker(String code, String name, String types) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/maint/workers",
                workerBody(code, name, types)));
        assertSuccess(resp, "维修人员[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> deviceBody(String code, String name, String type, String operator) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceCode", code);
        body.put("deviceName", name);
        body.put("deviceType", type);
        body.put("seaArea", "东山湾海区");
        body.put("operator", operator);
        return body;
    }

    private Map<String, Object> windowBody(LocalDateTime start, LocalDateTime end,
                                           String reason, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("windowStart", start);
        body.put("windowEnd", end);
        body.put("reason", reason);
        body.put("registeredBy", "调度员林工");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> workerBody(String code, String name, String types) {
        Map<String, Object> body = new HashMap<>();
        body.put("workerCode", code);
        body.put("workerName", name);
        body.put("qualifiedTypes", types);
        body.put("phone", "13800000000");
        return body;
    }

    private Map<String, Object> planBody(String no, Long deviceId, String name,
                                         Boolean hours, Integer intervalHours,
                                         Boolean calendar, Integer intervalDays, LocalDate anchor,
                                         List<Map<String, Object>> parts, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("planNo", no);
        body.put("deviceId", deviceId);
        body.put("planName", name);
        body.put("hoursEnabled", hours);
        if (intervalHours != null) {
            body.put("intervalHours", intervalHours);
        }
        body.put("calendarEnabled", calendar);
        if (intervalDays != null) {
            body.put("intervalDays", intervalDays);
        }
        if (anchor != null) {
            body.put("anchorDate", anchor);
        }
        body.put("parts", parts);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> partTpl(Long catalogId, int quantity) {
        Map<String, Object> line = new HashMap<>();
        line.put("catalogId", catalogId);
        line.put("quantity", quantity);
        return line;
    }

    private Map<String, Object> partLine(Long catalogId, int quantity) {
        return partTpl(catalogId, quantity);
    }

    private Map<String, Object> readingBody(int hours, LocalDate date, String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("readingHours", hours);
        body.put("readingDate", date);
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> manualBody(Long deviceId, Long windowId,
                                           LocalDateTime start, LocalDateTime end,
                                           List<Map<String, Object>> parts,
                                           String title, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceId", deviceId);
        body.put("downtimeWindowId", windowId);
        body.put("schedStart", start);
        body.put("schedEnd", end);
        body.put("dueAt", start);
        body.put("parts", parts);
        body.put("title", title);
        body.put("operator", "调度员林工");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> startBody(String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> replaceBody(String newSerial, String oldSerial,
                                            String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("newSerialNo", newSerial);
        body.put("removedSerialNo", oldSerial);
        body.put("replacedBy", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> acceptBody(String acceptedBy, String remark) {
        Map<String, Object> body = new HashMap<>();
        if (acceptedBy != null) {
            body.put("acceptedBy", acceptedBy);
        }
        body.put("acceptRemark", remark);
        return body;
    }

    private Map<String, Object> safetyBody(String confirmedBy, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("confirmedBy", confirmedBy);
        body.put("remark", remark);
        return body;
    }

    private Map<String, Object> closeBody(String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    @SafeVarargs
    private final <T> List<T> listOf(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) {
            list.add(item);
        }
        return list;
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
        sb.append("\n========== 养殖设备保养工单验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==============================================\n");
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
