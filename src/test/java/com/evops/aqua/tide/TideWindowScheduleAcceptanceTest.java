package com.evops.aqua.tide;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideNotification;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.mapper.TideChangeEvidenceMapper;
import com.evops.aqua.tide.mapper.TideNotificationMapper;
import com.evops.aqua.tide.mapper.TideScheduleMapper;
import com.evops.aqua.tide.mapper.TideWindowMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 潮汐窗口排班验收（aqua-5）。
 * 主线：登记潮汐表/禁作天气/人员班次/设备 → 按海区潮汐生成候选并叠加天气班次成窗
 * → 潮汐数据调到临界时间（跨午夜、端点相接）验证窗口按同一口径归属
 * → 排班绑窗口池塘设备（同一设备同一时间一口池）→ 发布后修改经调度审批
 * → 自动顺延原计划不变 → 临时关闭窗口通知班组并留回执 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TideWindowScheduleAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TideWindowMapper windowMapper;
    @Autowired
    private TideScheduleMapper scheduleMapper;
    @Autowired
    private TideNotificationMapper notificationMapper;
    @Autowired
    private TideChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pond1;      // 东山湾海区
    private Long pond2;      // 东山湾海区
    private Long pond3;      // 西沙湾海区（海区不匹配校验用）
    private Long device1;    // PUMP-01 东山湾
    private Long device2;    // PUMP-02 东山湾

    private final Map<String, Long> eventIds = new HashMap<>();   // requestId -> 潮汐事件id
    private final Map<String, Long> windowByEvent = new HashMap<>(); // 事件requestId -> 窗口id
    private Long schedule1;  // 池1+设备1 @ W(E1) 09:00-11:00
    private Long schedule2;  // 池2+设备1 @ W(E8) 11:00-13:00
    private Long schedule3;  // 池1+设备2 @ W(E8)（待发布直接改期用）
    private Long postponed;  // schedule2 顺延生成的新排班
    private Long notification1;

    @Test
    void acceptanceTideWindowSchedule() {
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

        checkpoint("CP1", "建池三口（两海区）并登记两台换水设备", () -> {
            pond1 = createPond("TD-P1", "养成一号池", "东山湾海区");
            pond2 = createPond("TD-P2", "养成二号池", "东山湾海区");
            pond3 = createPond("TD-P3", "西沙湾池", "西沙湾海区");
            device1 = createDevice("PUMP-01", "一号换水泵", "东山湾海区");
            device2 = createDevice("PUMP-02", "二号换水泵", "东山湾海区");
        });

        checkpoint("CP2", "登记潮汐表：含临界时间（跨午夜高潮）与低潮事件；缺操作者拒绝；重复登记幂等", () -> {
            Map<String, Object> noOperator = tideBody("东山湾海区", "2026-10-10T10:00:00", "HIGH", "ev-x");
            noOperator.remove("operator");
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/tide/events", noOperator));
            check(!denied.path("success").asBoolean(true), "缺操作者登记潮汐应被参数校验拒绝");

            // 高潮事件：E1正常、E2撞天气、E3/E8与天气端点相接、E4班次端点覆盖、E5无班次、
            // E6/E7跨午夜临界（E7在11日但窗口归10日）、E9为低潮不生成窗口
            registerEvent("ev-1", "东山湾海区", "2026-10-10T10:00:00", "HIGH");
            registerEvent("ev-2", "东山湾海区", "2026-10-10T14:00:00", "HIGH");
            registerEvent("ev-3", "东山湾海区", "2026-10-10T16:00:00", "HIGH");
            registerEvent("ev-4", "东山湾海区", "2026-10-10T18:00:00", "HIGH");
            registerEvent("ev-5", "东山湾海区", "2026-10-10T20:00:00", "HIGH");
            registerEvent("ev-6", "东山湾海区", "2026-10-10T23:50:00", "HIGH");
            registerEvent("ev-7", "东山湾海区", "2026-10-11T00:10:00", "HIGH");
            registerEvent("ev-8", "东山湾海区", "2026-10-10T12:00:00", "HIGH");
            registerEvent("ev-9", "东山湾海区", "2026-10-10T04:00:00", "LOW");

            // 同 requestId 重复登记：返回首次记录
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/tide/events",
                    tideBody("东山湾海区", "2026-10-10T10:00:00", "HIGH", "ev-1")));
            check(again.path("data").path("id").asLong() == eventIds.get("ev-1"),
                    "同requestId重复登记应返回首次潮汐记录");
            // 同海区同时刻同潮型换 requestId 再登记：自然键幂等，不产生第二条
            JsonNode sameFact = json(call(HttpMethod.POST, "/api/aqua/tide/events",
                    tideBody("东山湾海区", "2026-10-10T10:00:00", "HIGH", "ev-1b")));
            check(sameFact.path("data").path("id").asLong() == eventIds.get("ev-1"),
                    "同一条潮汐事实重复登记应返回首次记录");
            JsonNode events = json(call(HttpMethod.GET, "/api/aqua/tide/events", null));
            check(events.path("data").size() == 9, "潮汐表应为9条（8高潮+1低潮），实际="
                    + events.path("data").size());
        });

        checkpoint("CP3", "登记禁作天气（13:00-15:00大风）与人员班次（白班07-19、夜班22-次日06）", () -> {
            Map<String, Object> ban = new HashMap<>();
            ban.put("seaArea", "东山湾海区");
            ban.put("banStart", "2026-10-10T13:00:00");
            ban.put("banEnd", "2026-10-10T15:00:00");
            ban.put("weatherType", "大风");
            ban.put("reason", "阵风九级禁止水上作业");
            ban.put("operator", "气象员林风");
            ban.put("requestId", "ban-1");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/tide/weather-bans", ban)), "登记禁作天气");

            registerShift("shift-a", "班组A", "2026-10-10T07:00:00", "2026-10-10T19:00:00");
            registerShift("shift-c", "班组C", "2026-10-10T22:00:00", "2026-10-11T06:00:00");
        });

        checkpoint("CP4", "生成10-10窗口：候选叠加天气班次成5窗，E2撞天气/E5无班次被排除并说明原因；重复生成幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/tide/windows/generate",
                    generateBody("东山湾海区", "2026-10-10", "gen-1010")));
            assertSuccess(resp, "生成10-10窗口");
            JsonNode windows = resp.path("data").path("windows");
            check(windows.size() == 5, "10-10应成窗5个（E1/E3/E4/E6/E8），实际=" + windows.size());
            for (JsonNode w : windows) {
                String reqId = eventRequestIdOf(w.path("tideEventId").asLong());
                windowByEvent.put(reqId, w.path("id").asLong());
            }
            JsonNode excluded = resp.path("data").path("excluded");
            check(excluded.size() == 2, "应排除2个候选（E2/E5），实际=" + excluded.size());
            check(excluded.toString().contains("禁作天气"), "E2排除原因应说明禁作天气：" + excluded);
            check(excluded.toString().contains("无班组班次"), "E5排除原因应说明无班次覆盖：" + excluded);

            // 同 requestId 重复生成：返回首次结果，不多写
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/tide/windows/generate",
                    generateBody("东山湾海区", "2026-10-10", "gen-1010")));
            check(again.path("data").path("windows").size() == 5, "重复生成应返回首次5窗");
            // 换 requestId 再生成：同一潮汐事件只一个窗口，总量不变
            JsonNode third = json(call(HttpMethod.POST, "/api/aqua/tide/windows/generate",
                    generateBody("东山湾海区", "2026-10-10", "gen-1010-b")));
            check(third.path("data").path("windows").size() == 5, "再次生成应复用已有5窗");
            check(windowMapper.selectCount(new QueryWrapper<>()) == 5L, "窗口总数应仍为5，实际="
                    + windowMapper.selectCount(new QueryWrapper<>()));
        });

        checkpoint("CP5", "临界时间同一口径归属：跨午夜窗口归开始日；端点相接的天气不冲突、班次算覆盖", () -> {
            // E6：高潮23:50 → 窗口22:50-次日00:50，归属日=开始日10-10，班组C当班
            JsonNode w6 = json(call(HttpMethod.GET, "/api/aqua/tide/windows/" + windowByEvent.get("ev-6"), null));
            check("2026-10-10".equals(w6.path("data").path("windowDate").asText())
                            && w6.path("data").path("windowStart").asText().startsWith("2026-10-10T22:50")
                            && w6.path("data").path("windowEnd").asText().startsWith("2026-10-11T00:50")
                            && "班组C".equals(w6.path("data").path("crewName").asText()),
                    "跨午夜窗口应归开始日10-10且班组C当班，实际=" + w6.path("data"));
            // E3窗口15:00-17:00与天气13:00-15:00端点相接→不成冲突；E4窗口17:00-19:00被班次07-19端点覆盖
            JsonNode w3 = json(call(HttpMethod.GET, "/api/aqua/tide/windows/" + windowByEvent.get("ev-3"), null));
            check(w3.path("data").path("windowStart").asText().startsWith("2026-10-10T15:00"),
                    "与天气端点相接的E3应成窗（半开区间不重叠）");
            JsonNode w4 = json(call(HttpMethod.GET, "/api/aqua/tide/windows/" + windowByEvent.get("ev-4"), null));
            check(w4.path("data").path("windowEnd").asText().startsWith("2026-10-10T19:00")
                            && "班组A".equals(w4.path("data").path("crewName").asText()),
                    "班次端点覆盖的E4应成窗且班组A当班");

            // 潮汐数据调到临界：E7在10-11 00:10高潮，窗口23:10(10日)-01:10(11日)
            JsonNode gen11 = json(call(HttpMethod.POST, "/api/aqua/tide/windows/generate",
                    generateBody("东山湾海区", "2026-10-11", "gen-1011")));
            assertSuccess(gen11, "生成10-11窗口");
            JsonNode windows11 = gen11.path("data").path("windows");
            check(windows11.size() == 1, "10-11应仅E7成窗，实际=" + windows11.size());
            Long w7Id = windows11.get(0).path("id").asLong();
            windowByEvent.put("ev-7", w7Id);
            check("2026-10-10".equals(windows11.get(0).path("windowDate").asText())
                            && windows11.get(0).path("windowStart").asText().startsWith("2026-10-10T23:10"),
                    "11日临界潮汐的窗口应归开始日10-10，实际=" + windows11.get(0));

            // 同一口径查询：归属日10-10共6窗（含跨午夜的E6/E7），10-11为0窗
            JsonNode day10 = json(call(HttpMethod.GET, "/api/aqua/tide/windows?date=2026-10-10", null));
            check(day10.path("data").size() == 6, "按归属日10-10应查到6窗，实际="
                    + day10.path("data").size());
            JsonNode day11 = json(call(HttpMethod.GET, "/api/aqua/tide/windows?date=2026-10-11", null));
            check(day11.path("data").size() == 0, "按归属日10-11应查到0窗（跨午夜归开始日），实际="
                    + day11.path("data").size());
        });

        checkpoint("CP6", "排班：同一设备同一时间只能服务一口池（端点相接不算冲突）；海区不一致/同池同窗拒绝", () -> {
            // S1：池1+设备1 @ W(E1) 09:00-11:00
            JsonNode s1 = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(windowByEvent.get("ev-1"), pond1, device1, "sch-1")));
            assertSuccess(s1, "创建排班S1");
            schedule1 = s1.path("data").path("id").asLong();
            check("PLANNED".equals(s1.path("data").path("status").asText())
                            && "2026-10-10".equals(s1.path("data").path("schedDate").asText())
                            && "班组A".equals(s1.path("data").path("crewName").asText()),
                    "排班应继承窗口归属日与班组（同一口径）");

            // 同一设备同一时间只能服务一口池：池2+设备1 @ 同一窗口 → 拒绝
            JsonNode conflict = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(windowByEvent.get("ev-1"), pond2, device1, "sch-x1")));
            check(!conflict.path("success").asBoolean(true)
                            && conflict.path("message").asText().contains("同一设备同一时间只能服务一口池"),
                    "设备时间重叠应拒绝：" + conflict.path("message").asText());

            // 端点相接不算冲突：池2+设备1 @ W(E8) 11:00-13:00（与S1的09:00-11:00相接）→ 允许
            JsonNode s2 = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(windowByEvent.get("ev-8"), pond2, device1, "sch-2")));
            assertSuccess(s2, "端点相接的设备排班应允许（同一口径）");
            schedule2 = s2.path("data").path("id").asLong();

            // 海区不一致：西沙湾池排东山湾窗口 → 拒绝
            JsonNode wrongSea = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(windowByEvent.get("ev-1"), pond3, device2, "sch-x2")));
            check(!wrongSea.path("success").asBoolean(true)
                            && wrongSea.path("message").asText().contains("海区"),
                    "海区不一致应拒绝：" + wrongSea.path("message").asText());

            // 同池同窗：池1在W(E1)已有排班 → 拒绝
            JsonNode samePond = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(windowByEvent.get("ev-1"), pond1, device2, "sch-x3")));
            check(!samePond.path("success").asBoolean(true)
                            && samePond.path("message").asText().contains("同一池塘同一窗口"),
                    "同池同窗应拒绝：" + samePond.path("message").asText());

            // S3：池1+设备2 @ W(E8)，待发布改期测试用
            JsonNode s3 = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(windowByEvent.get("ev-8"), pond1, device2, "sch-3")));
            assertSuccess(s3, "创建排班S3");
            schedule3 = s3.path("data").path("id").asLong();
            check(scheduleMapper.selectCount(new QueryWrapper<>()) == 3L, "排班总数应为3，实际="
                    + scheduleMapper.selectCount(new QueryWrapper<>()));
        });

        checkpoint("CP7", "排班发布后修改必须经调度审批：无审批人拒绝；待发布排班直接改期", () -> {
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule1 + "/publish",
                    operatorBody("调度员周潮"))), "发布S1");

            // 已发布排班改期：缺审批人/审批意见 → 拒绝
            JsonNode noApproval = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule1 + "/reschedule",
                    rescheduleBody(windowByEvent.get("ev-3"), null, "调度员周潮", null, null, "rs-x1")));
            check(!noApproval.path("success").asBoolean(true)
                            && noApproval.path("message").asText().contains("调度审批"),
                    "发布后修改缺审批应拒绝：" + noApproval.path("message").asText());
            JsonNode onlyApprover = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule1 + "/reschedule",
                    rescheduleBody(windowByEvent.get("ev-3"), null, "调度员周潮", "调度长郑海", null, "rs-x2")));
            check(!onlyApprover.path("success").asBoolean(true), "缺审批意见同样应拒绝");

            // 审批齐全：S1 改到 W(E3) 15:00-17:00
            JsonNode approved = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule1 + "/reschedule",
                    rescheduleBody(windowByEvent.get("ev-3"), null, "调度员周潮", "调度长郑海",
                            "避让午后大风复盘，同意改期", "rs-1")));
            assertSuccess(approved, "审批齐全改期S1");
            check(approved.path("data").path("windowId").asLong() == windowByEvent.get("ev-3")
                            && approved.path("data").path("schedStart").asText().startsWith("2026-10-10T15:00"),
                    "S1应改至15:00-17:00窗口");

            // 待发布排班直接改期（无需审批）：S3 从 W(E8) 改到 W(E1)
            JsonNode direct = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule3 + "/reschedule",
                    rescheduleBody(windowByEvent.get("ev-1"), null, "调度员周潮", null, null, "rs-2")));
            assertSuccess(direct, "待发布排班应可直接改期");
            check(direct.path("data").path("windowId").asLong() == windowByEvent.get("ev-1"),
                    "S3应改至W(E1)窗口");

            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule2 + "/publish",
                    operatorBody("调度员周潮"))), "发布S2");
        });

        checkpoint("CP8", "自动顺延：跳过设备占用窗口生成新计划，原计划时间不变；重复顺延幂等；未发布不能顺延", () -> {
            // 未发布的S3不能顺延
            JsonNode notPublished = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule3 + "/postpone",
                    postponeBody("调度员周潮", "pp-x1")));
            check(!notPublished.path("success").asBoolean(true)
                            && notPublished.path("message").asText().contains("已发布"),
                    "未发布排班顺延应拒绝：" + notPublished.path("message").asText());

            // S2（设备1 @ 11:00-13:00）顺延：W(E3)15:00-17:00设备1被S1占用跳过 → 落W(E4)17:00-19:00
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule2 + "/postpone",
                    postponeBody("调度员周潮", "pp-1")));
            assertSuccess(resp, "自动顺延S2");
            postponed = resp.path("data").path("id").asLong();
            check(resp.path("data").path("originScheduleId").asLong() == schedule2
                            && resp.path("data").path("windowId").asLong() == windowByEvent.get("ev-4")
                            && resp.path("data").path("schedStart").asText().startsWith("2026-10-10T17:00")
                            && "PUBLISHED".equals(resp.path("data").path("status").asText()),
                    "顺延新计划应落17:00-19:00窗口并继承发布态，实际=" + resp.path("data"));

            // 原计划不变：S2时间/池塘/设备原样，仅状态POSTPONED
            TideSchedule origin = scheduleMapper.selectById(schedule2);
            check(TideSchedule.STATUS_POSTPONED.equals(origin.getStatus()), "原计划应置已顺延");
            check(origin.getSchedStart().toString().startsWith("2026-10-10T11:00")
                            && origin.getSchedEnd().toString().startsWith("2026-10-10T13:00")
                            && origin.getPondId().equals(pond2) && origin.getDeviceId().equals(device1)
                            && "2026-10-10".equals(origin.getSchedDate().toString()),
                    "自动顺延不能改变原计划：时间/池塘/设备应保持不变，实际=" + origin.getSchedStart()
                            + "~" + origin.getSchedEnd());

            // 重复顺延（同requestId）幂等：返回同一顺延单，不多写
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/tide/schedules/" + schedule2 + "/postpone",
                    postponeBody("调度员周潮", "pp-1")));
            check(again.path("data").path("id").asLong() == postponed, "重复顺延应返回同一排班");
            check(scheduleMapper.selectCount(new QueryWrapper<>()) == 4L, "排班总数应为4（3+1顺延），实际="
                    + scheduleMapper.selectCount(new QueryWrapper<>()));
        });

        checkpoint("CP9", "临时关闭窗口：通知受影响班组并留回执；重复签收/重复关闭幂等；关窗后不能排班", () -> {
            Long w4 = windowByEvent.get("ev-4");
            Map<String, Object> close = new HashMap<>();
            close.put("operator", "调度长郑海");
            close.put("reason", "上游溢油预警，临时停止换水");
            close.put("requestId", "close-1");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/tide/windows/" + w4 + "/close", close));
            assertSuccess(resp, "临时关闭窗口W(E4)");
            check("CLOSED".equals(resp.path("data").path("window").path("status").asText()), "窗口应已关闭");
            JsonNode notifications = resp.path("data").path("notifications");
            check(notifications.size() == 1
                            && "班组A".equals(notifications.get(0).path("crewName").asText())
                            && "SENT".equals(notifications.get(0).path("status").asText())
                            && notifications.get(0).path("message").asText().contains("上游溢油预警"),
                    "应向受影响班组A发一条通知：" + notifications);
            notification1 = notifications.get(0).path("id").asLong();

            // 关窗后不能再排班
            JsonNode afterClose = json(call(HttpMethod.POST, "/api/aqua/tide/schedules",
                    scheduleBody(w4, pond2, device2, "sch-x4")));
            check(!afterClose.path("success").asBoolean(true)
                            && afterClose.path("message").asText().contains("临时关闭"),
                    "关窗后排班应拒绝：" + afterClose.path("message").asText());

            // 班组签收留回执
            Map<String, Object> ack = new HashMap<>();
            ack.put("operator", "班组A组长老陈");
            ack.put("ackRemark", "已收到关闭通知，本班组17点换水任务取消");
            JsonNode acked = json(call(HttpMethod.POST, "/api/aqua/tide/notifications/" + notification1 + "/ack", ack));
            assertSuccess(acked, "班组签收");
            check("ACKED".equals(acked.path("data").path("status").asText())
                            && "班组A组长老陈".equals(acked.path("data").path("ackBy").asText())
                            && acked.path("data").path("ackRemark").asText().contains("已收到")
                            && acked.path("data").path("ackTime").asText().length() > 0,
                    "回执应含签收人/时间/备注：" + acked.path("data"));

            // 重复签收幂等：返回同一回执，不覆盖首次签收
            Map<String, Object> ackAgain = new HashMap<>();
            ackAgain.put("operator", "别人");
            ackAgain.put("ackRemark", "重复签收不应覆盖");
            JsonNode ack2 = json(call(HttpMethod.POST, "/api/aqua/tide/notifications/" + notification1 + "/ack", ackAgain));
            check("班组A组长老陈".equals(ack2.path("data").path("ackBy").asText()),
                    "重复签收不得覆盖首次回执");

            // 重复关闭幂等：不再多发通知
            JsonNode closeAgain = json(call(HttpMethod.POST, "/api/aqua/tide/windows/" + w4 + "/close", close));
            check(closeAgain.path("data").path("notifications").size() == 1, "重复关闭不应多发通知");
            check(notificationMapper.selectCount(new QueryWrapper<>()) == 1L, "通知总数应为1，实际="
                    + notificationMapper.selectCount(new QueryWrapper<>()));
        });

        checkpoint("CP10", "全程留证：各动作证据齐全且均带操作者，可按对象与requestId追溯", () -> {
            check(evidenceCount(TideChangeEvidence.ACTION_REGISTER_EVENT) == 9, "潮汐登记证据应为9条");
            check(evidenceCount(TideChangeEvidence.ACTION_REGISTER_BAN) == 1, "禁作天气证据应为1条");
            check(evidenceCount(TideChangeEvidence.ACTION_REGISTER_SHIFT) == 2, "班次证据应为2条");
            check(evidenceCount(TideChangeEvidence.ACTION_REGISTER_DEVICE) == 2, "设备证据应为2条");
            check(evidenceCount(TideChangeEvidence.ACTION_GENERATE_WINDOW) == 6, "成窗证据应为6条");
            check(evidenceCount(TideChangeEvidence.ACTION_CREATE_SCHEDULE) == 3, "排班创建证据应为3条");
            check(evidenceCount(TideChangeEvidence.ACTION_PUBLISH_SCHEDULE) == 2, "发布证据应为2条");
            check(evidenceCount(TideChangeEvidence.ACTION_RESCHEDULE_SCHEDULE) == 2, "改期证据应为2条");
            check(evidenceCount(TideChangeEvidence.ACTION_POSTPONE_SCHEDULE) == 1, "顺延证据应为1条");
            check(evidenceCount(TideChangeEvidence.ACTION_CLOSE_WINDOW) == 1, "关窗证据应为1条");
            check(evidenceCount(TideChangeEvidence.ACTION_NOTIFY_CREW) == 1, "通知证据应为1条");
            check(evidenceCount(TideChangeEvidence.ACTION_ACK_NOTIFICATION) == 1, "签收证据应为1条");
            List<TideChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            check(!all.isEmpty() && all.stream().allMatch(e -> e.getOperator() != null
                            && !e.getOperator().trim().isEmpty()),
                    "每条证据都必须带操作者，不得丢操作者");

            // 按对象追溯：S2 应有 创建+发布+顺延 三条证据
            JsonNode byEntity = json(call(HttpMethod.GET,
                    "/api/aqua/tide/evidence?entityType=SCHEDULE&entityId=" + schedule2, null));
            check(byEntity.path("data").size() == 3, "S2维度应查到创建/发布/顺延三条证据，实际="
                    + byEntity.path("data").size());
            // 按 requestId 追溯：顺延请求 pp-1 一条证据
            JsonNode byRequest = json(call(HttpMethod.GET, "/api/aqua/tide/evidence?requestId=pp-1", null));
            check(byRequest.path("data").size() == 1
                            && TideChangeEvidence.ACTION_POSTPONE_SCHEDULE.equals(
                            byRequest.path("data").get(0).path("action").asText()),
                    "requestId=pp-1应查到顺延证据");
            // 顺延证据快照应能证明原计划时间未变
            TideChangeEvidence postponeEvidence = evidenceMapper.selectOne(
                    new QueryWrapper<TideChangeEvidence>()
                            .eq("action", TideChangeEvidence.ACTION_POSTPONE_SCHEDULE));
            check(postponeEvidence.getBeforeJson().contains("11:00")
                            && postponeEvidence.getAfterJson().contains("11:00")
                            && postponeEvidence.getAfterJson().contains("POSTPONED"),
                    "顺延前后快照应证明原计划时间不变、仅状态变化");
        });

        report.add("验收结论：全部检查点通过——按海区潮汐表生成候选并叠加禁作天气与人员班次成窗、"
                + "临界时间按半开区间与开始日同一口径归属、同一设备同一时间一口池、"
                + "发布后修改经调度审批、自动顺延原计划不变、临时关闭通知班组并留回执、全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name, String seaArea) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", seaArea);
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long createDevice(String code, String name, String seaArea) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceCode", code);
        body.put("deviceName", name);
        body.put("seaArea", seaArea);
        body.put("operator", "设备员老金");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/tide/devices", body));
        assertSuccess(resp, "登记设备[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private void registerEvent(String requestId, String seaArea, String tideTime, String tideType) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/tide/events",
                tideBody(seaArea, tideTime, tideType, requestId)));
        assertSuccess(resp, "登记潮汐[" + requestId + "]");
        eventIds.put(requestId, resp.path("data").path("id").asLong());
    }

    private Map<String, Object> tideBody(String seaArea, String tideTime, String tideType, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("seaArea", seaArea);
        body.put("tideTime", tideTime);
        body.put("tideType", tideType);
        body.put("tideHeightCm", 420);
        body.put("source", "国家海洋预报台");
        body.put("operator", "录入员小许");
        body.put("requestId", requestId);
        return body;
    }

    private void registerShift(String requestId, String crew, String start, String end) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("crewName", crew);
        body.put("shiftStart", start);
        body.put("shiftEnd", end);
        body.put("operator", "排班员老顾");
        body.put("requestId", requestId);
        assertSuccess(json(call(HttpMethod.POST, "/api/aqua/tide/shifts", body)), "登记班次[" + crew + "]");
    }

    private Map<String, Object> generateBody(String seaArea, String date, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("seaArea", seaArea);
        body.put("date", date);
        body.put("marginMinutes", 60);
        body.put("operator", "调度员周潮");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> scheduleBody(Long windowId, Long pondId, Long deviceId, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("windowId", windowId);
        body.put("pondId", pondId);
        body.put("deviceId", deviceId);
        body.put("operator", "调度员周潮");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> operatorBody(String operator) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        return body;
    }

    private Map<String, Object> rescheduleBody(Long newWindowId, Long newDeviceId, String operator,
                                               String approver, String approveRemark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("newWindowId", newWindowId);
        if (newDeviceId != null) {
            body.put("newDeviceId", newDeviceId);
        }
        body.put("operator", operator);
        if (approver != null) {
            body.put("approver", approver);
        }
        if (approveRemark != null) {
            body.put("approveRemark", approveRemark);
        }
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> postponeBody(String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("requestId", requestId);
        body.put("reason", "气象复核不满足作业条件");
        return body;
    }

    private String eventRequestIdOf(Long eventId) {
        for (Map.Entry<String, Long> entry : eventIds.entrySet()) {
            if (entry.getValue().equals(eventId)) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("未登记的潮汐事件id=" + eventId);
    }

    private long evidenceCount(String action) {
        Long count = evidenceMapper.selectCount(
                new QueryWrapper<TideChangeEvidence>().eq("action", action));
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
        sb.append("\n========== 潮汐窗口排班验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==========================================\n");
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
