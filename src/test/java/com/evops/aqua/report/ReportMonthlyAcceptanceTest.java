package com.evops.aqua.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.DailyRecord;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.DailyRecordMapper;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.InspectionRecordMapper;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.med.entity.MedRequisition;
import com.evops.aqua.med.entity.MedRequisitionItem;
import com.evops.aqua.med.mapper.MedRequisitionItemMapper;
import com.evops.aqua.med.mapper.MedRequisitionMapper;
import com.evops.aqua.release.entity.ReleaseOrder;
import com.evops.aqua.release.entity.ReleaseRound;
import com.evops.aqua.release.mapper.ReleaseOrderMapper;
import com.evops.aqua.release.mapper.ReleaseRoundMapper;
import com.evops.aqua.report.entity.ReportCaliberVersion;
import com.evops.aqua.report.entity.ReportJob;
import com.evops.aqua.report.entity.ReportJobAttempt;
import com.evops.aqua.report.entity.ReportMonthly;
import com.evops.aqua.report.entity.ReportSeaGrant;
import com.evops.aqua.report.mapper.ReportCaliberVersionMapper;
import com.evops.aqua.report.mapper.ReportJobAttemptMapper;
import com.evops.aqua.report.mapper.ReportJobMapper;
import com.evops.aqua.report.mapper.ReportMonthlyMapper;
import com.evops.aqua.report.mapper.ReportSeaGrantMapper;
import com.evops.aqua.report.service.ReportSnapshotService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 水产监管月报验收（aqua-16）。
 * 主线：五指标按口径版本汇总 → Quartz 生成固化快照（缺采样标待补不填零）→
 * 重新生成只产生新版本 → 导出按海区权限过滤 → 失败任务保留异常链路与重试次数 →
 * 海水养殖管理缺权时口径管理入口拒绝（换地址不放行）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportMonthlyAcceptanceTest {

    private static final String MONTH = "2026-09";
    private static final String EAST = "验收东山湾海区";
    private static final String NORTH = "验收北部湾海区";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private DailyRecordMapper dailyMapper;
    @Autowired
    private HarvestRecordMapper harvestMapper;
    @Autowired
    private InspectionRecordMapper inspectionMapper;
    @Autowired
    private MedRequisitionMapper requisitionMapper;
    @Autowired
    private MedRequisitionItemMapper requisitionItemMapper;
    @Autowired
    private ReleaseOrderMapper releaseOrderMapper;
    @Autowired
    private ReleaseRoundMapper releaseRoundMapper;
    @Autowired
    private ReportCaliberVersionMapper caliberMapper;
    @Autowired
    private ReportMonthlyMapper monthlyMapper;
    @Autowired
    private ReportJobMapper jobMapper;
    @Autowired
    private ReportJobAttemptMapper attemptMapper;
    @Autowired
    private ReportSeaGrantMapper seaGrantMapper;
    @Autowired
    private ReportSnapshotService snapshotService;
    @Autowired
    private com.evops.aqua.report.service.ReportAccessService accessService;

    private String sessionCookie;
    private final List<String> report = new ArrayList<>();

    private Long eastPond1;
    private Long eastPond2;
    private Long northPond;
    private Long eastFirstReportId;

    @Test
    void acceptanceMonthlyReport() {
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

        checkpoint("CP1", "采样台账：两个海区四组数据（含跨月边界与不计入草稿单）；登录人预置全海区授权", () -> {
            seedData();
            grant("bootstrap", "*", "验收主管预置全海区授权（CP8 再验证过滤）");
            List<Pond> eastPonds = pondMapper.selectList(new QueryWrapper<Pond>()
                    .eq("sea_area", EAST));
            List<Pond> northPonds = pondMapper.selectList(new QueryWrapper<Pond>()
                    .eq("sea_area", NORTH));
            check(eastPonds.size() >= 2, "东山湾海区应至少2口池");
            check(northPonds.size() >= 1, "北部湾海区应至少1口池");
        });

        checkpoint("CP2", "首次生成自动建口径 V1：五指标逐一口径版本固化，汇总值正确", () -> {
            JsonNode resp = generate(EAST, "RPT-CP2-01");
            assertSuccess(resp, "首次月报生成");
            check("SUCCESS".equals(resp.path("data").path("status").asText()),
                    "任务应成功，实际=" + resp.path("data").path("status").asText());
            eastFirstReportId = resp.path("data").path("reportId").asLong();
            check(eastFirstReportId != null && eastFirstReportId > 0, "成功任务应回填快照ID");

            JsonNode detail = detail(eastFirstReportId);
            check("CAL-V1".equals(detail.path("caliberVersionNo").asText()),
                    "首版快照应固化 CAL-V1，实际=" + detail.path("caliberVersionNo").asText());
            check(detail.path("frozen").asBoolean(false), "快照生成后必须 frozen=TRUE");
            check(detail.path("versionSeq").asInt() == 1, "首张版本应为 V1");

            JsonNode indicators = detail.path("indicators");
            check(indicators.size() == 5, "应固化5个指标，实际=" + indicators.size());
            JsonNode byCode = indexByCode(indicators);
            check("150000".equals(byCode.path("SEEDLING").path("indicatorValue").asText()),
                    "投苗量应为150000（100000+50000，跨月8月批次不计入），实际="
                            + byCode.path("SEEDLING").path("indicatorValue").asText());
            check("20.00".equals(byCode.path("MEDICATION").path("indicatorValue").asText()),
                    "用药量应仅计 APPROVED 单 20.00（12.50+7.50，草稿单不计入），实际="
                            + byCode.path("MEDICATION").path("indicatorValue").asText());
            check(new BigDecimal("0.23").compareTo(new BigDecimal(
                    byCode.path("MORTALITY").path("indicatorValue").asText())) == 0,
                    "死亡率应为 350/150000*100=0.23%，实际="
                            + byCode.path("MORTALITY").path("indicatorValue").asText());
            check("30000".equals(byCode.path("HARVEST").path("indicatorValue").asText()),
                    "出池量应为30000，实际=" + byCode.path("HARVEST").path("indicatorValue").asText());
            check(byCode.path("INSPECTION").path("indicatorValue").asText().contains("合格2笔"),
                    "检验结论应为 合格2/不合格2，实际="
                            + byCode.path("INSPECTION").path("indicatorValue").asText());
            check(byCode.path("INSPECTION").path("indicatorValue").asText().contains("不合格2笔"),
                    "检验结论不合格笔数应为2");
            for (JsonNode ind : indicators) {
                check(ind.path("caliberJson").asText().contains("sourceTable"),
                        "每个指标必须固化统计口径 JSON：" + ind.path("indicatorCode").asText());
                check("CAL-V1".equals(ind.path("caliberVersionNo").asText()),
                        "每个指标必须带口径版本号 CAL-V1");
                check("NORMAL".equals(ind.path("dataStatus").asText()),
                        "数据齐全指标应为 NORMAL");
            }
        });

        checkpoint("CP3", "口径发布只能新版本：V2 发布后 V1 冻结 SUPERSEDED；requestId 幂等", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("remark", "死亡率分母改用月初在养量");
            body.put("requestId", "CAL-PUB-01");
            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> mortalityCaliber = new HashMap<>();
            mortalityCaliber.put("aggregation", "SUM(死亡数)/月初在养数量合计×100");
            mortalityCaliber.put("denominatorPolicy", "MONTH_START_STOCK");
            Map<String, Object> item = new HashMap<>();
            item.put("indicatorCode", "MORTALITY");
            item.put("caliber", mortalityCaliber);
            items.add(item);
            body.put("indicators", items);
            JsonNode pub = json(call(HttpMethod.POST, "/api/aqua/report/calibers", body));
            assertSuccess(pub, "口径V2发布");
            check("CAL-V2".equals(pub.path("data").path("versionNo").asText()),
                    "新版本号应为 CAL-V2");
            check("ACTIVE".equals(pub.path("data").path("status").asText()), "V2 应为 ACTIVE");

            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/report/calibers", body));
            assertSuccess(replay, "重复 requestId 发布应幂等返回首次版本");
            check("CAL-V2".equals(replay.path("data").path("versionNo").asText()),
                    "重复发布不得产生 CAL-V3");

            ReportCaliberVersion v1 = caliberMapper.selectOne(new QueryWrapper<ReportCaliberVersion>()
                    .eq("version_no", "CAL-V1"));
            check("SUPERSEDED".equals(v1.getStatus()), "V1 应冻结为 SUPERSEDED，实际=" + v1.getStatus());
            long versionCount = caliberMapper.selectCount(new QueryWrapper<>());
            check(versionCount == 2, "口径版本应只有2个，实际=" + versionCount);
        });

        checkpoint("CP4", "重新生成只产生新版本：月报 V2 引用 CAL-V2，旧快照 V1 永不回改", () -> {
            JsonNode resp = generate(EAST, "RPT-CP4-01");
            assertSuccess(resp, "第二次月报生成");
            Long secondId = resp.path("data").path("reportId").asLong();
            JsonNode detail2 = detail(secondId);
            check(detail2.path("versionSeq").asInt() == 2, "第二次生成应为月报 V2");
            check("CAL-V2".equals(detail2.path("caliberVersionNo").asText()),
                    "新月报应引用新口径 CAL-V2");

            JsonNode detail1 = detail(eastFirstReportId);
            check(detail1.path("versionSeq").asInt() == 1, "旧月报必须仍是 V1");
            check("CAL-V1".equals(detail1.path("caliberVersionNo").asText()),
                    "旧月报口径版本必须仍是 CAL-V1，不被新口径回改");
            check(detail1.path("frozen").asBoolean(false), "旧快照必须保持固化");
            JsonNode inds1 = indexByCode(detail1.path("indicators"));
            check(inds1.path("MORTALITY").path("caliberJson").asText().contains("涉及批次当前在养数量"),
                    "旧快照死亡率口径必须保留 V1 文本");

            long eastCount = monthlyMapper.selectCount(new QueryWrapper<ReportMonthly>()
                    .eq("report_month", MONTH).eq("sea_area", EAST));
            check(eastCount == 2, "东山湾该月应有2个版本快照，实际=" + eastCount);
        });

        checkpoint("CP5", "缺采样标待补绝不填零：北部湾仅有投苗，其余四指标 PENDING_FILL 且值为空", () -> {
            JsonNode resp = generate(NORTH, "RPT-CP5-01");
            assertSuccess(resp, "北部湾月报生成");
            Long northId = resp.path("data").path("reportId").asLong();
            JsonNode detail = detail(northId);
            check(detail.path("pendingFillCount").asInt() == 4,
                    "应4项待补，实际=" + detail.path("pendingFillCount").asInt());
            JsonNode byCode = indexByCode(detail.path("indicators"));
            check("80000".equals(byCode.path("SEEDLING").path("indicatorValue").asText()),
                    "北部湾投苗量应为80000");
            for (String code : new String[]{"MEDICATION", "MORTALITY", "HARVEST", "INSPECTION"}) {
                JsonNode ind = byCode.path(code);
                check("PENDING_FILL".equals(ind.path("dataStatus").asText()),
                        code + " 应为 PENDING_FILL，实际=" + ind.path("dataStatus").asText());
                check(ind.path("indicatorValue").isNull() || ind.path("indicatorValue").asText().isEmpty(),
                        code + " 待补时指标值必须留空，绝不能填0");
                check(ind.path("pendingReason").asText().length() > 0,
                        code + " 待补必须写明原因");
            }
            String csv = exportCsv(MONTH, null);
            check(csv.contains("待补(CAL-V1)") || csv.contains("待补(CAL-V2)"),
                    "CSV 导出待补指标必须显示待补而非0");
        });

        checkpoint("CP6", "Quartz 同逻辑扫描：逐海区生成，每海区独立任务；扫描可重跑且只增新版本", () -> {
            JsonNode scan = json(call(HttpMethod.POST, "/api/aqua/report/jobs/scan?reportMonth=" + MONTH, null));
            assertSuccess(scan, "手工触发Quartz同逻辑扫描");
            // 共享验收库中可能存在其他验收模块遗留的池塘海区；本检查点只核对本测试两个海区
            check(scan.path("data").path("areaCount").asInt() >= 2,
                    "扫描海区数应至少2个，实际=" + scan.path("data").path("areaCount").asInt());
            check(scan.path("data").path("failedCount").asInt() == 0,
                    "扫描不应有失败，实际=" + scan.path("data").path("failedCount").asInt());
            int seen = 0;
            for (JsonNode item : scan.path("data").path("items")) {
                if (EAST.equals(item.path("seaArea").asText()) || NORTH.equals(item.path("seaArea").asText())) {
                    seen++;
                    check("SUCCESS".equals(item.path("status").asText()),
                            "海区任务应成功：" + item.path("seaArea").asText());
                    check(item.path("attempts").asInt() == 1,
                            item.path("seaArea").asText() + "首次扫描尝试次数应为1");
                }
            }
            check(seen == 2, "扫描结果必须覆盖本测试的两个海区，实际覆盖=" + seen);
            long eastCount = monthlyMapper.selectCount(new QueryWrapper<ReportMonthly>()
                    .eq("report_month", MONTH).eq("sea_area", EAST));
            long northCount = monthlyMapper.selectCount(new QueryWrapper<ReportMonthly>()
                    .eq("report_month", MONTH).eq("sea_area", NORTH));
            check(eastCount == 3, "东山湾应为3个版本（2手工+1扫描），实际=" + eastCount);
            check(northCount == 2, "北部湾应为2个版本（1手工+1扫描），实际=" + northCount);
        });

        checkpoint("CP7", "失败任务保留异常链路与重试次数：FAILED+堆栈不删，重试成功历史失败仍在", () -> {
            snapshotService.armFault(1, "注入故障：采样数据汇总不可用");
            JsonNode failed = generate(NORTH, "RPT-CP7-FAIL");
            assertSuccess(failed, "失败请求接口仍正常响应任务行");
            Long jobId = failed.path("data").path("id").asLong();
            check("FAILED".equals(failed.path("data").path("status").asText()),
                    "任务应 FAILED，实际=" + failed.path("data").path("status").asText());
            check(failed.path("data").path("attempts").asInt() == 1, "首次失败 attempts 应为1");
            check(failed.path("data").path("lastError").asText().contains("注入故障"),
                    "last_error 必须保留异常消息");
            check(failed.path("data").path("errorChain").asText().contains("异常链路"),
                    "error_chain 必须保留异常链路文本");

            List<ReportJobAttempt> attempts = attemptMapper.selectList(
                    new QueryWrapper<ReportJobAttempt>().eq("job_id", jobId).orderByAsc("attempt_no"));
            check(attempts.size() == 1 && "FAILED".equals(attempts.get(0).getStatus()),
                    "应留1条失败尝试明细");
            check(attempts.get(0).getExceptionClass().contains("ReportGenerateException"),
                    "异常类型必须保留：" + attempts.get(0).getExceptionClass());
            check(attempts.get(0).getStackTrace().contains("ReportSnapshotService"),
                    "异常堆栈必须保留");
            long northCountBefore = monthlyMapper.selectCount(new QueryWrapper<ReportMonthly>()
                    .eq("report_month", MONTH).eq("sea_area", NORTH));

            // 非失败任务不可重试
            ReportJob successJob = jobMapper.selectOne(new QueryWrapper<ReportJob>()
                    .eq("sea_area", EAST).eq("status", "SUCCESS").orderByDesc("id").last("limit 1"));
            JsonNode retryBad = json(call(HttpMethod.POST,
                    "/api/aqua/report/jobs/" + successJob.getId() + "/retry", null));
            check(!retryBad.path("success").asBoolean(true), "成功任务不应允许重试");

            JsonNode retry = json(call(HttpMethod.POST,
                    "/api/aqua/report/jobs/" + jobId + "/retry", null));
            assertSuccess(retry, "重试失败任务");
            check("SUCCESS".equals(retry.path("data").path("status").asText()), "重试后应成功");
            check(retry.path("data").path("attempts").asInt() == 2,
                    "重试后 attempts 应递增为2，实际=" + retry.path("data").path("attempts").asInt());
            check(retry.path("data").path("reportId").asLong() > 0, "重试成功应回填新快照ID");
            attempts = attemptMapper.selectList(new QueryWrapper<ReportJobAttempt>()
                    .eq("job_id", jobId).orderByAsc("attempt_no"));
            check(attempts.size() == 2, "两次尝试链路都必须保留，实际=" + attempts.size());
            check("FAILED".equals(attempts.get(0).getStatus())
                            && "SUCCESS".equals(attempts.get(1).getStatus()),
                    "第1次失败、第2次成功的链路必须逐次保留，重试成功不清除失败链路");
            long northCountAfter = monthlyMapper.selectCount(new QueryWrapper<ReportMonthly>()
                    .eq("report_month", MONTH).eq("sea_area", NORTH));
            check(northCountAfter == northCountBefore + 1,
                    "失败当次不得留半成品快照，重试成功才新增1版");
            snapshotService.disarmFault();
        });

        checkpoint("CP8", "导出按海区权限过滤：无授权不可见、单海区只见本区、越权详情403、CSV同源", () -> {
            seaGrantMapper.delete(new QueryWrapper<ReportSeaGrant>().eq("username", "bootstrap"));

            JsonNode noneList = json(call(HttpMethod.GET,
                    "/api/aqua/report/monthly?reportMonth=" + MONTH, null));
            assertSuccess(noneList, "月报列表");
            check(noneList.path("data").size() == 0,
                    "未登记海区授权时不应看到任何海区月报，实际=" + noneList.path("data").size());
            String noneCsv = exportCsv(MONTH, null);
            check(!noneCsv.contains(EAST) && !noneCsv.contains(NORTH),
                    "无授权时CSV也必须按海区过滤，不得出现任何海区数据");

            grant("bootstrap", EAST, "仅东山湾");
            JsonNode eastList = json(call(HttpMethod.GET,
                    "/api/aqua/report/monthly?reportMonth=" + MONTH, null));
            assertSuccess(eastList, "东山湾授权列表");
            for (JsonNode row : eastList.path("data")) {
                check(EAST.equals(row.path("seaArea").asText()), "列表不得混入未授权北部湾数据");
            }

            Long northReportId = monthlyMapper.selectOne(new QueryWrapper<ReportMonthly>()
                    .eq("report_month", MONTH).eq("sea_area", NORTH).orderByDesc("version_seq")
                    .last("limit 1")).getId();
            ResponseEntity<String> cross = call(HttpMethod.GET,
                    "/api/aqua/report/monthly/" + northReportId, null);
            check(cross.getStatusCodeValue() == 403,
                    "越权查看北部湾月报必须403，实际=" + cross.getStatusCodeValue());
            ResponseEntity<byte[]> crossExport = exportRaw(MONTH, NORTH);
            String crossCsv = new String(crossExport.getBody());
            check(!crossCsv.contains(NORTH) || crossCsv.split("\r\n").length <= 2,
                    "导出未授权海区不得返回其数据行");

            String eastCsv = exportCsv(MONTH, EAST);
            check(eastCsv.contains(EAST) && !eastCsv.contains(NORTH),
                    "CSV 必须只含授权的东山湾数据");
            check(eastCsv.contains("CAL-V1") || eastCsv.contains("CAL-V2"),
                    "CSV 每指标必须保留统计口径版本号");

            grant("bootstrap", "*", "全部海区");
            JsonNode allList = json(call(HttpMethod.GET,
                    "/api/aqua/report/monthly?reportMonth=" + MONTH, null));
            check(allList.path("data").size() >= 5,
                    "* 授权应见全部海区全部版本，实际=" + allList.path("data").size());
        });

        checkpoint("CP9", "缺权口径入口拒绝：收紧后标准/尾斜杠/只读入口403；服务闸门与URL无关；换地址不放行", () -> {
            // 先放开并确认可访问
            toggleFeedAccess("relax", "验收恢复");
            ResponseEntity<String> ok = call(HttpMethod.POST,
                    "/api/aqua/report/calibers", publishBody("CAL-RELAX-CHECK", "放开时应可访问"));
            check(ok.getStatusCodeValue() == 200, "放开时口径管理应可访问");
            long beforeCount = caliberMapper.selectCount(new QueryWrapper<>());

            toggleFeedAccess("tighten", "验收模拟海水养殖管理缺权");

            // 服务闸门（与调用地址无关的第二道防线）：即使请求不经口径管理 URL（内部转发/直调），
            // 只要到达口径管理逻辑，缺权一律拒绝
            boolean gateThrew = false;
            try {
                accessService.requireCaliberManageAllowed(
                        "/internal/forwarded/from-other-entry", "INTERNAL-CALL", "bootstrap");
            } catch (com.evops.aqua.report.web.ReportAccessDeniedException expected) {
                gateThrew = true;
            }
            check(gateThrew, "服务闸门对任意内部调用地址都必须拒绝（换调用地址不放行）");

            // 可达的换地址变体：标准地址、尾斜杠、只读列表/详情入口——入口过滤器必须一律 403
            String[] blockedUris = {
                    "/api/aqua/report/calibers",
                    "/api/aqua/report/calibers/",
            };
            for (String uri : blockedUris) {
                ResponseEntity<String> denied = rawCall(HttpMethod.POST, uri,
                        publishBody("CAL-DENIED-" + uri.hashCode(), "换地址尝试"));
                check(denied.getStatusCodeValue() == 403,
                        "缺权时口径管理地址 " + uri + " 必须403拒绝，实际=" + denied.getStatusCodeValue());
                JsonNode body = objectMapper.readTree(denied.getBody());
                check(!body.path("success").asBoolean(true), "拒绝响应必须 success=false");
            }
            ResponseEntity<String> deniedGet = call(HttpMethod.GET, "/api/aqua/report/calibers", null);
            check(deniedGet.getStatusCodeValue() == 403, "缺权时口径版本查询入口也必须403拒绝");
            ResponseEntity<String> deniedDetail = call(HttpMethod.GET, "/api/aqua/report/calibers/1", null);
            check(deniedDetail.getStatusCodeValue() == 403, "缺权时口径版本详情入口也必须403拒绝");

            // 容器层拒绝的路径变体（分号矩阵变量/双斜杠，Tomcat 直接 400）：同样不得放行，不得产生新版本
            String[] rejectedUris = {
                    "/api/aqua/report/calibers;bypass=1",
                    "/api/aqua/report//calibers",
            };
            for (String uri : rejectedUris) {
                ResponseEntity<String> rejected = rawCall(HttpMethod.POST, uri,
                        publishBody("CAL-REJECT-" + uri.hashCode(), "路径变体尝试"));
                check(rejected.getStatusCodeValue() < 200 || rejected.getStatusCodeValue() >= 300,
                        "路径变体 " + uri + " 不得放行，实际=" + rejected.getStatusCodeValue());
            }

            // 月报查询不属口径管理，仍应可用
            ResponseEntity<String> monthlyOk = call(HttpMethod.GET,
                    "/api/aqua/report/monthly?reportMonth=" + MONTH, null);
            check(monthlyOk.getStatusCodeValue() == 200, "缺权仅封口径管理，月报查询不应受影响");

            long afterCount = caliberMapper.selectCount(new QueryWrapper<>());
            check(afterCount == beforeCount,
                    "换地址尝试不得产生新口径版本，before=" + beforeCount + " after=" + afterCount);

            JsonNode evidence = json(call(HttpMethod.GET,
                    "/api/aqua/report/evidence?entityType=ACCESS_GATE", null));
            assertSuccess(evidence, "缺权拒绝证据查询");
            check(evidence.path("data").size() >= 4,
                    "每次入口拒绝都必须独立事务留证，实际证据数=" + evidence.path("data").size());
            check(evidence.path("data").toString().contains("CALIBER_ACCESS_DENIED"),
                    "证据动作应为 CALIBER_ACCESS_DENIED");

            toggleFeedAccess("relax", "验收结束放开");
            ResponseEntity<String> back = call(HttpMethod.POST,
                    "/api/aqua/report/calibers", publishBody("CAL-RELAX-OK", "放开后恢复"));
            check(back.getStatusCodeValue() == 200, "放开后口径管理应恢复可访问");
        });

        checkpoint("CP10", "检验结论逐笔固化：台账抽检与放行检验轮次都进快照且可分类核对", () -> {
            JsonNode detail = detail(eastFirstReportId);
            JsonNode inspections = detail.path("inspections");
            int ledger = 0;
            int rounds = 0;
            boolean sawPass = false;
            boolean sawFail = false;
            for (JsonNode row : inspections) {
                String source = row.path("sourceTable").asText();
                if ("LEDGER_INSPECTION".equals(source)) {
                    ledger++;
                }
                if ("RELEASE_ROUND".equals(source)) {
                    rounds++;
                }
                String conclusion = row.path("conclusion").asText();
                if (conclusion.contains("合格") || "PASS".equalsIgnoreCase(conclusion)) {
                    sawPass = true;
                }
                if (conclusion.contains("不合格") || "FAIL".equalsIgnoreCase(conclusion)) {
                    sawFail = true;
                }
            }
            check(ledger == 2, "应固化2笔台账抽检，实际=" + ledger);
            check(rounds == 2, "应固化2笔放行检验轮次，实际=" + rounds);
            check(sawPass && sawFail, "合格/不合格结论都应逐笔保留");
        });

        checkpoint("CP11", "全程证据链：发布/固化/失败/重试/拒绝/导出/授权均可经 evidence 追溯", () -> {
            String[] actions = {"CALIBER_PUBLISH", "SNAPSHOT_FROZEN", "JOB_FAILED", "JOB_RETRY",
                    "JOB_SUCCESS", "CALIBER_ACCESS_DENIED", "REPORT_EXPORT", "SEA_GRANT_UPSERT"};
            JsonNode all = json(call(HttpMethod.GET, "/api/aqua/report/evidence", null));
            assertSuccess(all, "证据总表");
            String text = all.path("data").toString();
            for (String action : actions) {
                check(text.contains(action), "证据链缺少动作：" + action);
            }
            JsonNode byReq = json(call(HttpMethod.GET,
                    "/api/aqua/report/evidence?requestId=CAL-PUB-01", null));
            check(byReq.path("data").size() >= 1, "应可按 requestId 追溯口径发布证据");
        });

        printReport();
    }

    // ---------------- 数据准备 ----------------

    private void seedData() {
        eastPond1 = insertPond("P-RPT-D1", EAST);
        eastPond2 = insertPond("P-RPT-D2", EAST);
        northPond = insertPond("P-RPT-B1", NORTH);

        insertBatch("B-RPT-E1", eastPond1, 100000, LocalDate.of(2026, 9, 2));
        insertBatch("B-RPT-E2", eastPond2, 50000, LocalDate.of(2026, 9, 5));
        insertBatch("B-RPT-E0", eastPond1, 99999, LocalDate.of(2026, 8, 31));
        insertBatch("B-RPT-N1", northPond, 80000, LocalDate.of(2026, 9, 8));

        insertDaily(eastPond1, batchOf("B-RPT-E1"), LocalDate.of(2026, 9, 10), 100);
        insertDaily(eastPond1, batchOf("B-RPT-E1"), LocalDate.of(2026, 9, 11), 200);
        insertDaily(eastPond2, batchOf("B-RPT-E2"), LocalDate.of(2026, 9, 12), 50);

        MedRequisition req = new MedRequisition();
        req.setReqNo("MED-RPT-01");
        req.setPondId(eastPond1);
        req.setDiseaseDesc("验收细菌性疾病防控");
        req.setUseDate(LocalDate.of(2026, 9, 9));
        req.setWaterVolumeM3(new BigDecimal("1200.00"));
        req.setStatus(MedRequisition.STATUS_APPROVED);
        req.setApplicant("验收员");
        req.setApprovedBy("验收兽医");
        req.setApprovedTime(LocalDateTime.of(2026, 9, 9, 10, 0));
        req.setRequestId("MED-RPT-01");
        requisitionMapper.insert(req);
        insertReqItem(req.getId(), new BigDecimal("12.50"));
        insertReqItem(req.getId(), new BigDecimal("7.50"));
        MedRequisition draft = new MedRequisition();
        draft.setReqNo("MED-RPT-DRAFT");
        draft.setPondId(eastPond1);
        draft.setDiseaseDesc("草稿单不应计入月报");
        draft.setUseDate(LocalDate.of(2026, 9, 15));
        draft.setWaterVolumeM3(new BigDecimal("100.00"));
        draft.setStatus(MedRequisition.STATUS_DRAFT);
        draft.setApplicant("验收员");
        draft.setRequestId("MED-RPT-DRAFT");
        requisitionMapper.insert(draft);
        insertReqItem(draft.getId(), new BigDecimal("500.00"));

        HarvestRecord harvest = new HarvestRecord();
        harvest.setPondId(eastPond1);
        harvest.setBatchId(batchOf("B-RPT-E1"));
        harvest.setHarvestDate(LocalDate.of(2026, 9, 20));
        harvest.setQuantity(30000);
        harvestMapper.insert(harvest);

        insertInspection(eastPond1, batchOf("B-RPT-E1"), LocalDate.of(2026, 9, 13), "合格");
        insertInspection(eastPond2, batchOf("B-RPT-E2"), LocalDate.of(2026, 9, 18), "不合格");

        ReleaseOrder order = new ReleaseOrder();
        order.setOrderNo("REL-RPT-01");
        order.setBatchId(batchOf("B-RPT-E1"));
        order.setPondId(eastPond1);
        order.setSpeciesCode("PRAWN");
        order.setSpeciesName("南美白对虾");
        order.setUsageCode("EDIBLE");
        order.setUsageName("食用");
        order.setRuleVersionId(1L);
        order.setStatus(ReleaseOrder.STATUS_SUBMITTED);
        order.setCurrentRoundNo(2);
        releaseOrderMapper.insert(order);
        insertRound(order.getId(), 1, "PASS", LocalDateTime.of(2026, 9, 16, 9, 0));
        insertRound(order.getId(), 2, "FAIL", LocalDateTime.of(2026, 9, 22, 9, 0));
    }

    private Long insertPond(String code, String area) {
        Pond pond = new Pond();
        pond.setPondCode(code + "-" + area.substring(2, 4));
        pond.setPondName(code);
        pond.setSeaArea(area);
        pond.setWaterDepthM(new BigDecimal("2.00"));
        pond.setSalinityPpt(new BigDecimal("30.0"));
        pond.setStatus(Pond.STATUS_ACTIVE);
        pondMapper.insert(pond);
        return pond.getId();
    }

    private void insertBatch(String no, Long pondId, int qty, LocalDate stockDate) {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setBatchNo(no);
        batch.setPondId(pondId);
        batch.setSpecies("南美白对虾");
        batch.setSeedSource("验收育苗场-" + no);
        batch.setQuantity(qty);
        batch.setStockDate(stockDate);
        batch.setPlannedHarvestDate(stockDate.plusMonths(4));
        batch.setStatus(SeedlingBatch.STATUS_STOCKED);
        batchMapper.insert(batch);
    }

    private void insertDaily(Long pondId, Long batchId, LocalDate date, int deaths) {
        DailyRecord daily = new DailyRecord();
        daily.setPondId(pondId);
        daily.setBatchId(batchId);
        daily.setRecordDate(date);
        daily.setMortalityCount(deaths);
        dailyMapper.insert(daily);
    }

    private void insertReqItem(Long reqId, BigDecimal qty) {
        MedRequisitionItem item = new MedRequisitionItem();
        item.setRequisitionId(reqId);
        item.setDrugId(1L);
        item.setMedBatchId(1L);
        item.setDosagePerM3(new BigDecimal("0.5000"));
        item.setEstimatedQty(qty);
        item.setWithdrawalDays(7);
        requisitionItemMapper.insert(item);
    }

    private void insertInspection(Long pondId, Long batchId, LocalDate date, String result) {
        InspectionRecord rec = new InspectionRecord();
        rec.setPondId(pondId);
        rec.setBatchId(batchId);
        rec.setInspectDate(date);
        rec.setSampleCount(30);
        rec.setResult(result);
        inspectionMapper.insert(rec);
    }

    private void insertRound(Long orderId, int roundNo, String conclusion, LocalDateTime submitted) {
        ReleaseRound round = new ReleaseRound();
        round.setOrderId(orderId);
        round.setRoundNo(roundNo);
        round.setRuleVersionId(1L);
        round.setStatus(ReleaseRound.STATUS_SUBMITTED);
        round.setConclusion(conclusion);
        round.setConclusionDetail("验收第" + roundNo + "轮结论 " + conclusion);
        round.setSubmittedBy("验收员");
        round.setSubmittedTime(submitted);
        releaseRoundMapper.insert(round);
    }

    private Long batchOf(String batchNo) {
        SeedlingBatch batch = batchMapper.selectOne(new QueryWrapper<SeedlingBatch>()
                .eq("batch_no", batchNo).last("limit 1"));
        check(batch != null, "测试批次不存在：" + batchNo);
        return batch.getId();
    }

    // ---------------- 接口辅助 ----------------

    private JsonNode generate(String area, String requestId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reportMonth", MONTH);
        body.put("seaArea", area);
        body.put("requestId", requestId);
        return json(call(HttpMethod.POST, "/api/aqua/report/monthly/generate", body));
    }

    private JsonNode detail(Long id) throws Exception {
        return json(call(HttpMethod.GET, "/api/aqua/report/monthly/" + id, null)).path("data");
    }

    private JsonNode indexByCode(JsonNode indicators) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode ind : indicators) {
            map.put(ind.path("indicatorCode").asText(), ind);
        }
        return objectMapper.valueToTree(map);
    }

    private void grant(String username, String areas, String remark) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("seaAreas", areas);
        body.put("remark", remark);
        assertSuccess(json(call(HttpMethod.POST, "/api/aqua/report/sea-grants", body)), "海区授权");
    }

    private void toggleFeedAccess(String action, String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("requestId", "FEED-ACCESS-" + action + "-RPT");
        assertSuccess(json(call(HttpMethod.POST, "/api/aqua/feed/access/" + action, body)),
                "饲料权限" + action);
    }

    private Map<String, Object> publishBody(String requestId, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", requestId);
        body.put("remark", remark);
        return body;
    }

    private String exportCsv(String month, String area) throws Exception {
        ResponseEntity<byte[]> resp = exportRaw(month, area);
        check(resp.getStatusCodeValue() == 200, "导出应200，实际=" + resp.getStatusCodeValue());
        return new String(resp.getBody(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private ResponseEntity<byte[]> exportRaw(String month, String area) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        // 用 URI 模板变量传中文海区，避免手工 URLEncoder 被 TestRestTemplate 二次编码
        if (area == null) {
            return rest.exchange("/api/aqua/report/monthly/export?reportMonth={month}",
                    HttpMethod.GET, new HttpEntity<>(headers), byte[].class, month);
        }
        return rest.exchange(
                "/api/aqua/report/monthly/export?reportMonth={month}&seaArea={area}",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class, month, area);
    }

    // ---------------- 框架 ----------------

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
        sb.append("\n========== 水产监管月报验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==========================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        return rawCall(method, url, body);
    }

    private ResponseEntity<String> rawCall(HttpMethod method, String url, Object body) {
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
