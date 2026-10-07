package com.evops.aqua.qual;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseFollowupTask;
import com.evops.aqua.disease.mapper.DiseaseFollowupTaskMapper;
import com.evops.aqua.maint.entity.DowntimeWindow;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintWorker;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.entity.WorkOrderWorker;
import com.evops.aqua.maint.mapper.DowntimeWindowMapper;
import com.evops.aqua.maint.mapper.MaintDeviceMapper;
import com.evops.aqua.maint.mapper.MaintWorkerMapper;
import com.evops.aqua.maint.mapper.WorkOrderMapper;
import com.evops.aqua.maint.mapper.WorkOrderWorkerMapper;
import com.evops.aqua.qual.entity.QualReminder;
import com.evops.aqua.qual.mapper.QualReminderMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 养殖人员资质与岗位授权验收（aqua-15）。
 * 主线：四岗位范围配置 → 人员登记 → 证书门控权限 → 临时授权（结束时间+审批单）→
 * 证书/授权过期立即收回 → 到期前提醒幂等 → 岗位变更保留历史 →
 * 批量冻结列出受影响工单与未完成任务 → 列表/详情/导出/日志交叉核对 → 解冻恢复。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonnelQualificationAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private QualReminderMapper reminderMapper;
    @Autowired
    private MaintWorkerMapper workerMapper;
    @Autowired
    private MaintDeviceMapper deviceMapper;
    @Autowired
    private DowntimeWindowMapper windowMapper;
    @Autowired
    private WorkOrderMapper orderMapper;
    @Autowired
    private WorkOrderWorkerMapper orderWorkerMapper;
    @Autowired
    private DiseaseFollowupTaskMapper followupTaskMapper;

    private String sessionCookie;
    private final List<String> report = new ArrayList<>();

    private Long vetId;
    private Long samplerId;
    private Long fisherId;
    private Long equipId;
    private Long samplerCertId;
    private Long expiredTempAuthId;

    @Test
    void acceptancePersonnelQualification() {
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

        checkpoint("CP1", "岗位范围：四岗位默认范围可查；范围可配置并留证；非法岗位/权限码拒绝", () -> {
            JsonNode roles = json(call(HttpMethod.GET, "/api/aqua/qual/roles", null));
            assertSuccess(roles, "岗位范围查询");
            check(roles.path("data").size() == 4, "应预置4个岗位范围，实际=" + roles.path("data").size());
            check(roles.path("data").toString().contains("VET")
                    && roles.path("data").toString().contains("SAMPLER")
                    && roles.path("data").toString().contains("FISHER")
                    && roles.path("data").toString().contains("EQUIPMENT_OPERATOR"), "岗位应覆盖兽医/采样员/捕捞员/设备员");

            Map<String, Object> scope = new HashMap<>();
            scope.put("menuCodes", "menu:aqua-fishing,menu:aqua-release");
            scope.put("apiPerms", "api:/api/aqua/fishing/**,api:/api/aqua/release/**");
            scope.put("requiredCertType", "FISHING_CERT");
            scope.put("operator", "资质主管");
            scope.put("remark", "捕捞员增加放行菜单");
            JsonNode updated = json(call(HttpMethod.PUT, "/api/aqua/qual/roles/FISHER/scope", scope));
            assertSuccess(updated, "捕捞员范围配置");
            check(updated.path("data").path("menuCodes").asText().contains("menu:aqua-release"),
                    "范围调整后菜单应包含 menu:aqua-release");

            Map<String, Object> badCode = new HashMap<>(scope);
            badCode.put("menuCodes", "aqua-fishing");
            JsonNode bad = json(call(HttpMethod.PUT, "/api/aqua/qual/roles/FISHER/scope", badCode));
            check(!bad.path("success").asBoolean(true), "权限码缺 menu: 前缀应被拒绝");

            JsonNode badRole = json(call(HttpMethod.PUT, "/api/aqua/qual/roles/CLEANER/scope", scope));
            check(!badRole.path("success").asBoolean(true), "未知岗位应被拒绝");

            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/qual/evidence?entityType=ROLE_SCOPE", null));
            check(evidence.path("data").toString().contains("ROLE_SCOPE_UPDATE"), "范围调整应留证据");
        });

        checkpoint("CP2", "人员登记：四岗位各一人；requestId 幂等；非法岗位与重复编号拒绝", () -> {
            vetId = registerPerson("QVET-01", "王兽医", "VET", "QUAL-P-01");
            samplerId = registerPerson("QSMP-01", "李采样", "SAMPLER", "QUAL-P-02");
            fisherId = registerPerson("QFISH-01", "赵捕捞", "FISHER", "QUAL-P-03");
            equipId = registerPerson("QEQP-01", "钱设备", "EQUIPMENT_OPERATOR", "QUAL-P-04");

            Long replay = registerPerson("QVET-01", "王兽医", "VET", "QUAL-P-01");
            check(replay.equals(vetId), "重复 requestId 应返回首次人员，不重复建档");

            JsonNode badRole = json(call(HttpMethod.POST, "/api/aqua/qual/persons",
                    personBody("QBAD-01", "非法岗位", "CLEANER", "QUAL-P-BAD-1")));
            check(!badRole.path("success").asBoolean(true), "非法岗位登记应被拒绝");

            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/qual/persons",
                    personBody("QVET-01", "重复编号", "VET", "QUAL-P-BAD-2")));
            check(!dup.path("success").asBoolean(true), "重复人员编号应被拒绝");
        });

        checkpoint("CP3", "证书门控：持有效证书获岗位菜单与接口权限；无证书人员接口菜单全拒", () -> {
            registerCert("QVET-01", "VET-LIC-001", "VET_LICENSE",
                    LocalDate.now().plusDays(365), "QUAL-C-01");
            check(checkPerm("QVET-01", "menu:aqua-med"), "兽医应可访问用药菜单");
            check(checkPerm("QVET-01", "api:/api/aqua/med/requisitions"), "兽医应可调用用药接口（前缀匹配）");
            check(!checkPerm("QVET-01", "menu:aqua-wq"), "兽医不应访问水质采样菜单");

            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/qual/access/check",
                    checkBody("QSMP-01", "menu:aqua-wq")));
            check(!denied.path("data").path("allowed").asBoolean(true), "无证书采样员应被拒绝");
            check(denied.path("data").path("reason").asText().contains("证书"),
                    "拒绝原因应说明证书无效或缺失，实际=" + denied.path("data").path("reason").asText());
        });

        checkpoint("CP4", "临时授权：必须带结束时间与审批单；生效中可越岗位授权；requestId 幂等", () -> {
            Map<String, Object> noEnd = tempAuthBody("QSMP-01", "menu:aqua-feed",
                    null, null, "SP-2026-X", "QUAL-T-BAD-1");
            JsonNode bad1 = json(call(HttpMethod.POST, "/api/aqua/qual/temp-auths", noEnd));
            check(!bad1.path("success").asBoolean(true), "缺结束时间的临时授权应被拒绝");

            Map<String, Object> noTicket = tempAuthBody("QSMP-01", "menu:aqua-feed",
                    null, LocalDateTime.now().plusDays(1), null, "QUAL-T-BAD-2");
            JsonNode bad2 = json(call(HttpMethod.POST, "/api/aqua/qual/temp-auths", noTicket));
            check(!bad2.path("success").asBoolean(true), "缺审批单号的临时授权应被拒绝");

            Long authId = grantTempAuth("QSMP-01", "menu:aqua-feed,api:/api/aqua/feed/**",
                    null, LocalDateTime.now().plusDays(2), "SP-2026-001", "QUAL-T-01");
            check(checkPerm("QSMP-01", "menu:aqua-feed"), "临时授权生效中应可访问岗位外菜单");
            check(checkPerm("QSMP-01", "api:/api/aqua/feed/orders"), "临时授权接口前缀应匹配");

            Long replay = grantTempAuth("QSMP-01", "menu:aqua-feed,api:/api/aqua/feed/**",
                    null, LocalDateTime.now().plusDays(2), "SP-2026-001", "QUAL-T-01");
            check(replay.equals(authId), "重复 requestId 应返回首次授权，不重复授予");
            JsonNode auths = json(call(HttpMethod.GET,
                    "/api/aqua/qual/temp-auths?personId=" + samplerId, null));
            check(auths.path("data").size() == 1, "幂等重放后授权记录应仍只有1条，实际="
                    + auths.path("data").size());
        });

        checkpoint("CP5", "临时授权到期：到点立即失效（实时判定），扫描置 EXPIRED 留证", () -> {
            expiredTempAuthId = grantTempAuth("QSMP-01", "menu:aqua-cost",
                    LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1),
                    "SP-2025-099", "QUAL-T-02");
            check(!checkPerm("QSMP-01", "menu:aqua-cost"),
                    "已过结束时间的临时授权应立即失效，不等扫描");

            json(call(HttpMethod.POST, "/api/aqua/qual/scan", null));
            JsonNode auths = json(call(HttpMethod.GET,
                    "/api/aqua/qual/temp-auths?personId=" + samplerId, null));
            String status = null;
            for (JsonNode auth : auths.path("data")) {
                if (auth.path("id").asLong() == expiredTempAuthId) {
                    status = auth.path("status").asText();
                }
            }
            check("EXPIRED".equals(status), "扫描后过期授权应置 EXPIRED，实际=" + status);
            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/qual/evidence?entityType=TEMP_AUTH&entityId=" + expiredTempAuthId, null));
            check(evidence.path("data").toString().contains("TEMP_AUTH_EXPIRED"),
                    "授权过期应留 TEMP_AUTH_EXPIRED 证据");
        });

        checkpoint("CP6", "证书过期：过期当刻权限立即收回；扫描置 EXPIRED 留证；新证恢复", () -> {
            registerCert("QEQP-01", "EQP-CERT-OLD", "EQUIPMENT_CERT",
                    LocalDate.now().minusDays(1), "QUAL-C-02");
            check(!checkPerm("QEQP-01", "menu:aqua-maint"),
                    "证书已过期应立即收回设备菜单权限（扫描前实时判定）");

            json(call(HttpMethod.POST, "/api/aqua/qual/scan", null));
            JsonNode certs = json(call(HttpMethod.GET,
                    "/api/aqua/qual/certificates?personId=" + equipId, null));
            boolean expiredSeen = false;
            for (JsonNode cert : certs.path("data")) {
                if ("EQP-CERT-OLD".equals(cert.path("certNo").asText())) {
                    expiredSeen = "EXPIRED".equals(cert.path("status").asText());
                }
            }
            check(expiredSeen, "扫描后过期证书应置 EXPIRED");
            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/qual/evidence?entityType=CERTIFICATE", null));
            check(evidence.path("data").toString().contains("CERT_EXPIRED"), "证书过期应留 CERT_EXPIRED 证据");

            registerCert("QEQP-01", "EQP-CERT-NEW", "EQUIPMENT_CERT",
                    LocalDate.now().plusDays(365), "QUAL-C-03");
            check(checkPerm("QEQP-01", "menu:aqua-maint"), "补登有效证书后设备菜单权限应恢复");
        });

        checkpoint("CP7", "到期前提醒：30天内到期证书逐证逐日提醒，重扫不重复", () -> {
            samplerCertId = registerCert("QSMP-01", "SMP-CERT-001", "SAMPLING_CERT",
                    LocalDate.now().plusDays(10), "QUAL-C-04");
            json(call(HttpMethod.POST, "/api/aqua/qual/scan", null));
            JsonNode reminders = json(call(HttpMethod.GET,
                    "/api/aqua/qual/reminders?personId=" + samplerId, null));
            assertSuccess(reminders, "提醒查询");
            boolean found = false;
            for (JsonNode reminder : reminders.path("data")) {
                if (reminder.path("certificateId").asLong() == samplerCertId) {
                    found = true;
                    check(reminder.path("daysLeft").asInt() == 10,
                            "剩余天数应为10，实际=" + reminder.path("daysLeft").asInt());
                }
            }
            check(found, "10天后到期的证书应生成到期提醒");

            json(call(HttpMethod.POST, "/api/aqua/qual/scan", null));
            Long count = reminderMapper.selectCount(new QueryWrapper<QualReminder>()
                    .eq("certificate_id", samplerCertId));
            check(count != null && count == 1, "重扫不应重复提醒，实际提醒数=" + count);
            check(checkPerm("QSMP-01", "menu:aqua-wq"), "证书仍在有效期内，权限应正常");
        });

        checkpoint("CP8", "岗位变更：权限按新岗位即时切换；历史操作记录保留不删；requestId 幂等", () -> {
            Map<String, Object> change = new HashMap<>();
            change.put("toRole", "SAMPLER");
            change.put("reason", "转岗至水质采样组");
            change.put("operator", "人事主管");
            change.put("requestId", "QUAL-R-01");
            JsonNode changed = json(call(HttpMethod.POST,
                    "/api/aqua/qual/persons/" + vetId + "/role-change", change));
            assertSuccess(changed, "岗位变更");
            check("VET".equals(changed.path("data").path("fromRole").asText())
                    && "SAMPLER".equals(changed.path("data").path("toRole").asText()),
                    "变更记录应写明 VET → SAMPLER");

            check(!checkPerm("QVET-01", "menu:aqua-med"), "变更后原岗位菜单应立即收回");
            check(!checkPerm("QVET-01", "menu:aqua-wq"), "新岗位所需证书未登记前仍应拒绝");
            registerCert("QVET-01", "SMP-CERT-101", "SAMPLING_CERT",
                    LocalDate.now().plusDays(365), "QUAL-C-05");
            check(checkPerm("QVET-01", "menu:aqua-wq"), "补登采样证书后新岗位权限应生效");

            JsonNode detail = json(call(HttpMethod.GET, "/api/aqua/qual/persons/" + vetId, null));
            check(detail.path("data").path("roleChanges").size() == 1,
                    "岗位变更历史应有1条，实际=" + detail.path("data").path("roleChanges").size());
            check(detail.path("data").path("certificates").size() == 2,
                    "历史证书应保留不删，实际=" + detail.path("data").path("certificates").size());

            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/qual/evidence?entityType=PERSON&entityId=" + vetId, null));
            String actions = evidence.path("data").toString();
            check(actions.contains("PERSON_REGISTER") && actions.contains("ROLE_CHANGE"),
                    "历史操作证据（登记/变更）应全部保留");

            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/qual/persons/" + vetId + "/role-change", change));
            check(replay.path("success").asBoolean(false)
                    && replay.path("data").path("id").asLong()
                    == changed.path("data").path("id").asLong(), "重复 requestId 应回放首次变更");
            JsonNode detail2 = json(call(HttpMethod.GET, "/api/aqua/qual/persons/" + vetId, null));
            check(detail2.path("data").path("roleChanges").size() == 1, "幂等重放不应新增变更历史");
        });

        checkpoint("CP9", "批量冻结：逐人列出受影响工单与未完成任务；冻结即收回权限；requestId 幂等", () -> {
            prepareFreezeFixtures();
            check(checkPerm("QEQP-01", "menu:aqua-maint"), "冻结前设备员权限应正常");

            Map<String, Object> freeze = new HashMap<>();
            freeze.put("personCodes", Arrays.asList("QEQP-01", "QSMP-01", "QNOPE-01"));
            freeze.put("reason", "季度资质复核冻结");
            freeze.put("operator", "资质主管");
            freeze.put("requestId", "QUAL-F-01");
            JsonNode frozen = json(call(HttpMethod.POST, "/api/aqua/qual/persons/batch-freeze", freeze));
            assertSuccess(frozen, "批量冻结");
            JsonNode lines = frozen.path("data").path("lines");
            check(lines.size() == 3, "冻结明细应3行，实际=" + lines.size());

            JsonNode equipLine = findLine(lines, "QEQP-01");
            check("FROZEN".equals(equipLine.path("result").asText()), "设备员应冻结成功");
            check(equipLine.path("affectedOrders").toString().contains("QUAL-WO-01"),
                    "应列出受影响工单 QUAL-WO-01，实际=" + equipLine.path("affectedOrders"));
            JsonNode samplerLine = findLine(lines, "QSMP-01");
            check("FROZEN".equals(samplerLine.path("result").asText()), "采样员应冻结成功");
            check(samplerLine.path("unfinishedTasks").toString().contains("QUAL-FT-01"),
                    "应列出未完成任务 QUAL-FT-01，实际=" + samplerLine.path("unfinishedTasks"));
            check("NOT_FOUND".equals(findLine(lines, "QNOPE-01").path("result").asText()),
                    "不存在人员应记 NOT_FOUND");

            check(!checkPerm("QEQP-01", "menu:aqua-maint"), "冻结后全部权限应立即收回");
            check(!checkPerm("QSMP-01", "menu:aqua-wq"), "冻结后采样员权限应立即收回");

            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/qual/persons/batch-freeze", freeze));
            check(replay.path("data").path("replayed").asBoolean(false), "重复 requestId 应命中幂等回放");
            check(replay.path("data").path("lines").size() == 3, "回放应返回首次3行明细，不重复冻结");
        });

        checkpoint("CP10", "交叉核对：列表/详情/导出/日志同一口径，冻结结果处处一致", () -> {
            JsonNode list = json(call(HttpMethod.GET, "/api/aqua/qual/persons?status=FROZEN", null));
            assertSuccess(list, "冻结人员列表");
            check(list.path("data").size() == 2, "冻结列表应恰好2人，实际=" + list.path("data").size());
            String listText = list.path("data").toString();
            check(listText.contains("QEQP-01") && listText.contains("QSMP-01"),
                    "冻结列表应含设备员与采样员");

            ResponseEntity<String> exportResp = call(HttpMethod.GET,
                    "/api/aqua/qual/persons/export?status=FROZEN", null);
            String csv = exportResp.getBody();
            check(csv != null, "导出应有内容");
            String[] csvLines = csv.trim().split("\r\n");
            check(csvLines.length == list.path("data").size() + 1,
                    "导出行数（含表头）应与列表行数+1一致，实际=" + csvLines.length);
            check(csv.contains("QEQP-01") && csv.contains("QSMP-01"), "导出应含两名冻结人员");
            check(csv.contains("FROZEN"), "导出应带冻结状态");

            JsonNode detail = json(call(HttpMethod.GET, "/api/aqua/qual/persons/" + equipId, null));
            check("FROZEN".equals(detail.path("data").path("person").path("status").asText()),
                    "详情状态应与列表一致为 FROZEN");
            check(detail.path("data").path("certificates").size() == 2,
                    "详情应含2张证书（过期+有效）");
            check(detail.path("data").path("permissions").path("menus").size() == 0,
                    "冻结人员有效菜单应为空");

            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/qual/evidence?entityType=PERSON&entityId=" + equipId, null));
            String actions = evidence.path("data").toString();
            check(actions.contains("PERSON_REGISTER") && actions.contains("PERSON_FREEZE"),
                    "日志应同时含登记与冻结证据");

            JsonNode allList = json(call(HttpMethod.GET, "/api/aqua/qual/persons", null));
            ResponseEntity<String> allExport = call(HttpMethod.GET, "/api/aqua/qual/persons/export", null);
            String[] allCsvLines = allExport.getBody() == null
                    ? new String[0] : allExport.getBody().trim().split("\r\n");
            check(allCsvLines.length == allList.path("data").size() + 1,
                    "全量导出行数应与全量列表一致，列表=" + allList.path("data").size()
                            + " 导出行=" + allCsvLines.length);
            for (JsonNode row : allList.path("data")) {
                check(allExport.getBody().contains(row.path("personCode").asText()),
                        "导出缺少人员 " + row.path("personCode").asText());
            }
        });

        checkpoint("CP11", "解冻恢复：解冻后权限按证书重新生效并留证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("operator", "资质主管");
            JsonNode unfrozen = json(call(HttpMethod.POST,
                    "/api/aqua/qual/persons/" + equipId + "/unfreeze", body));
            assertSuccess(unfrozen, "解冻");
            check("ACTIVE".equals(unfrozen.path("data").path("status").asText()), "解冻后应恢复在职");
            check(checkPerm("QEQP-01", "menu:aqua-maint"), "解冻后设备菜单权限应恢复（证书仍有效）");
            check(!checkPerm("QSMP-01", "menu:aqua-wq"), "未解冻人员权限应仍收回");

            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/qual/evidence?entityType=PERSON&entityId=" + equipId, null));
            check(evidence.path("data").toString().contains("PERSON_UNFREEZE"), "解冻应留证据");
        });

        report.add("验收结论：通过");
        printReport();
    }

    // ---------- 业务调用 ----------

    private Long registerPerson(String code, String name, String role, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/qual/persons",
                personBody(code, name, role, requestId)));
        assertSuccess(resp, "登记人员" + code);
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> personBody(String code, String name, String role, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("personCode", code);
        body.put("personName", name);
        body.put("roleCode", role);
        body.put("requestId", requestId);
        return body;
    }

    private Long registerCert(String personCode, String certNo, String certType,
                              LocalDate expiresAt, String requestId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("personCode", personCode);
        body.put("certNo", certNo);
        body.put("certType", certType);
        body.put("issuedAt", expiresAt.minusDays(400).toString());
        body.put("expiresAt", expiresAt.toString());
        body.put("operator", "资质主管");
        body.put("requestId", requestId);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/qual/certificates", body));
        assertSuccess(resp, "登记证书" + certNo);
        return resp.path("data").path("id").asLong();
    }

    private Long grantTempAuth(String personCode, String permCodes, LocalDateTime from,
                               LocalDateTime to, String ticket, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/qual/temp-auths",
                tempAuthBody(personCode, permCodes, from, to, ticket, requestId)));
        assertSuccess(resp, "临时授权" + requestId);
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> tempAuthBody(String personCode, String permCodes, LocalDateTime from,
                                             LocalDateTime to, String ticket, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("personCode", personCode);
        body.put("permCodes", permCodes);
        if (from != null) {
            body.put("effectiveFrom", from.toString());
        }
        if (to != null) {
            body.put("effectiveTo", to.toString());
        }
        if (ticket != null) {
            body.put("approvalTicket", ticket);
        }
        body.put("reason", "临时顶岗");
        body.put("operator", "资质主管");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> checkBody(String personCode, String permission) {
        Map<String, Object> body = new HashMap<>();
        body.put("personCode", personCode);
        body.put("permission", permission);
        return body;
    }

    private boolean checkPerm(String personCode, String permission) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/qual/access/check",
                checkBody(personCode, permission)));
        assertSuccess(resp, "权限核验");
        return resp.path("data").path("allowed").asBoolean(false);
    }

    private JsonNode findLine(JsonNode lines, String personCode) {
        for (JsonNode line : lines) {
            if (personCode.equals(line.path("personCode").asText())) {
                return line;
            }
        }
        throw new IllegalStateException("冻结明细缺少人员 " + personCode);
    }

    /** 冻结影响夹具：设备员关联一张未关闭保养工单，采样员关联一条待处理复查任务（均按自然键幂等）。 */
    private void prepareFreezeFixtures() {
        MaintWorker worker = workerMapper.selectOne(
                new QueryWrapper<MaintWorker>().eq("worker_code", "QEQP-01"));
        if (worker == null) {
            worker = new MaintWorker();
            worker.setWorkerCode("QEQP-01");
            worker.setWorkerName("钱设备");
            worker.setQualifiedTypes("AERATOR");
            worker.setStatus(MaintWorker.STATUS_ACTIVE);
            workerMapper.insert(worker);
        }
        MaintDevice device = deviceMapper.selectOne(
                new QueryWrapper<MaintDevice>().eq("device_code", "QUAL-DEV-01"));
        if (device == null) {
            device = new MaintDevice();
            device.setDeviceCode("QUAL-DEV-01");
            device.setDeviceName("资质验收增氧机");
            device.setDeviceType("AERATOR");
            device.setStatus("RUNNING");
            device.setOperator("资质主管");
            deviceMapper.insert(device);
        }
        DowntimeWindow window = windowMapper.selectOne(
                new QueryWrapper<DowntimeWindow>().eq("window_no", "QUAL-W-01"));
        if (window == null) {
            window = new DowntimeWindow();
            window.setWindowNo("QUAL-W-01");
            window.setDeviceId(device.getId());
            window.setWindowStart(LocalDateTime.now().plusDays(1));
            window.setWindowEnd(LocalDateTime.now().plusDays(1).plusHours(2));
            window.setReason("资质验收停机窗口");
            window.setStatus(DowntimeWindow.STATUS_OPEN);
            window.setRegisteredBy("资质主管");
            window.setRequestId("QUAL-FIX-W-01");
            windowMapper.insert(window);
        }
        WorkOrder order = orderMapper.selectOne(
                new QueryWrapper<WorkOrder>().eq("order_no", "QUAL-WO-01"));
        if (order == null) {
            order = new WorkOrder();
            order.setOrderNo("QUAL-WO-01");
            order.setDeviceId(device.getId());
            order.setTriggerSource("MANUAL");
            order.setDueAt(LocalDateTime.now().plusDays(1));
            order.setDowntimeWindowId(window.getId());
            order.setSchedStart(window.getWindowStart());
            order.setSchedEnd(window.getWindowEnd());
            order.setStatus("ASSIGNED");
            order.setRequestId("QUAL-FIX-WO-01");
            orderMapper.insert(order);
        }
        Long linkCount = orderWorkerMapper.selectCount(new QueryWrapper<WorkOrderWorker>()
                .eq("work_order_id", order.getId()).eq("worker_id", worker.getId()));
        if (linkCount == null || linkCount == 0) {
            WorkOrderWorker link = new WorkOrderWorker();
            link.setWorkOrderId(order.getId());
            link.setWorkerId(worker.getId());
            link.setAssignedBy("资质主管");
            link.setAssignedAt(LocalDateTime.now());
            orderWorkerMapper.insert(link);
        }
        DiseaseFollowupTask task = followupTaskMapper.selectOne(
                new QueryWrapper<DiseaseFollowupTask>().eq("task_no", "QUAL-FT-01"));
        if (task == null) {
            task = new DiseaseFollowupTask();
            task.setTaskNo("QUAL-FT-01");
            task.setObservationId(0L);
            task.setTaskDate(LocalDate.now());
            task.setStatus("PENDING");
            task.setOperator("QSMP-01");
            task.setSourceObserver("QSMP-01");
            task.setRemark("资质验收复查任务");
            followupTaskMapper.insert(task);
        }
    }

    // ---------- 框架 ----------

    private interface CheckpointBody {
        void run() throws Exception;
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
            fail("验收" + stop, t);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 养殖人员资质与岗位授权验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("====================================================\n");
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
}
