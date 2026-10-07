package com.evops.aqua;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.DailyRecord;
import com.evops.aqua.entity.FeedRecord;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.MedicationRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.entity.TransferRecord;
import com.evops.aqua.mapper.DailyRecordMapper;
import com.evops.aqua.mapper.FeedRecordMapper;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.InspectionRecordMapper;
import com.evops.aqua.mapper.MedicationRecordMapper;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.mapper.TransferRecordMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 海水养殖台账验收：从养殖池档案入口打通到出池。
 * 每个检查点既走 HTTP 接口，也直接查库核对落库行；
 * 任一检查点失败即停，报告中给出停点与原因。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AquaLedgerAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private TransferRecordMapper transferMapper;
    @Autowired
    private DailyRecordMapper dailyMapper;
    @Autowired
    private FeedRecordMapper feedMapper;
    @Autowired
    private MedicationRecordMapper medicationMapper;
    @Autowired
    private InspectionRecordMapper inspectionMapper;
    @Autowired
    private HarvestRecordMapper harvestMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId1;
    private Long pondId2;
    private Long batchId;
    private final String batchNo = "B20261003-001";

    @Test
    void acceptanceFromPondArchiveToHarvest() {
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

        checkpoint("CP1", "养殖池档案入口：建池落库（水深/盐度/海区）", () -> {
            pondId1 = createPond("POND-A01", "东山1号池", "东山湾海区", "1.80", "28.5");
            pondId2 = createPond("POND-A02", "东山2号池", "东山湾海区", "2.00", "29.0");
            Pond pond = pondMapper.selectById(pondId1);
            check(pond != null, "t_pond 查无档案行，id=" + pondId1);
            check("东山湾海区".equals(pond.getSeaArea()), "海区落库不符：" + pond.getSeaArea());
            check(new BigDecimal("1.80").compareTo(pond.getWaterDepthM()) == 0, "水深落库不符：" + pond.getWaterDepthM());
            check(new BigDecimal("28.5").compareTo(pond.getSalinityPpt()) == 0, "盐度落库不符：" + pond.getSalinityPpt());
        });

        checkpoint("CP2", "投苗：投苗批次落库（苗种来源/入池数量/计划出池日）", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("batchNo", batchNo);
            body.put("pondId", pondId1);
            body.put("species", "南美白对虾");
            body.put("seedSource", "漳州东山育苗场");
            body.put("quantity", 120000);
            body.put("stockDate", "2026-10-03");
            body.put("plannedHarvestDate", "2027-01-15");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
            assertSuccess(resp, "投苗");
            batchId = resp.path("data").path("id").asLong();

            SeedlingBatch row = batchMapper.selectOne(
                    new QueryWrapper<SeedlingBatch>().eq("batch_no", batchNo));
            check(row != null, "t_seedling_batch 查无批次行，batch_no=" + batchNo);
            check(row.getId().equals(batchId), "落库行主键与接口返回不一致");
            check(row.getPondId().equals(pondId1), "批次落库池号不符：" + row.getPondId());
            check("漳州东山育苗场".equals(row.getSeedSource()), "苗种来源落库不符：" + row.getSeedSource());
            check(Integer.valueOf(120000).equals(row.getQuantity()), "入池数量落库不符：" + row.getQuantity());
            check(LocalDate.of(2027, 1, 15).equals(row.getPlannedHarvestDate()),
                    "计划出池日落库不符：" + row.getPlannedHarvestDate());
            check(SeedlingBatch.STATUS_STOCKED.equals(row.getStatus()), "批次状态应为在养：" + row.getStatus());
        });

        checkpoint("CP3", "批次编号不能重复", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("batchNo", batchNo);
            body.put("pondId", pondId1);
            body.put("species", "南美白对虾");
            body.put("seedSource", "另一育苗场");
            body.put("quantity", 50000);
            body.put("stockDate", "2026-10-03");
            body.put("plannedHarvestDate", "2027-02-01");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
            check(!resp.path("success").asBoolean(true), "重复批次编号未被拦截");
            check(resp.path("message").asText().contains("批次编号已存在"),
                    "重复提示语不符：" + resp.path("message").asText());
            Long count = batchMapper.selectCount(new QueryWrapper<SeedlingBatch>().eq("batch_no", batchNo));
            check(count != null && count == 1L, "库中同编号批次行数应为1，实际=" + count);
        });

        checkpoint("CP4", "日常记录按池按批落库", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("pondId", pondId1);
            body.put("batchId", batchId);
            body.put("recordDate", "2026-10-04");
            body.put("waterDepthM", "1.80");
            body.put("salinityPpt", "28.4");
            body.put("waterTempC", "26.5");
            body.put("mortalityCount", 12);
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/records/daily", body));
            assertSuccess(resp, "日常记录");
            List<DailyRecord> rows = dailyMapper.selectList(new QueryWrapper<DailyRecord>()
                    .eq("pond_id", pondId1).eq("batch_id", batchId));
            check(rows.size() == 1, "t_daily_record 按池按批查询行数应为1，实际=" + rows.size());
        });

        checkpoint("CP5", "饲料/用药按池按批留痕且可追溯", () -> {
            Map<String, Object> feed = new HashMap<>();
            feed.put("pondId", pondId1);
            feed.put("batchId", batchId);
            feed.put("feedDate", "2026-10-04");
            feed.put("feedName", "对虾配合饲料2号");
            feed.put("quantityKg", "15.50");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/records/feed", feed)), "饲料登记");

            Map<String, Object> med = new HashMap<>();
            med.put("pondId", pondId1);
            med.put("batchId", batchId);
            med.put("useDate", "2026-10-05");
            med.put("medicineName", "聚维酮碘");
            med.put("dosage", "0.5ppm");
            med.put("reason", "预防性消毒");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/records/medication", med)), "用药登记");

            List<FeedRecord> feeds = feedMapper.selectList(new QueryWrapper<FeedRecord>()
                    .eq("pond_id", pondId1).eq("batch_id", batchId));
            check(feeds.size() == 1, "t_feed_record 按池按批查询行数应为1，实际=" + feeds.size());
            List<MedicationRecord> meds = medicationMapper.selectList(new QueryWrapper<MedicationRecord>()
                    .eq("pond_id", pondId1).eq("batch_id", batchId));
            check(meds.size() == 1, "t_medication_record 按池按批查询行数应为1，实际=" + meds.size());

            JsonNode trace = json(call(HttpMethod.GET, "/api/aqua/batches/" + batchId + "/trace", null));
            assertSuccess(trace, "批次追溯");
            check(trace.path("data").path("feedRecords").size() == 1, "追溯中饲料记录缺失");
            check(trace.path("data").path("medicationRecords").size() == 1, "追溯中用药记录缺失");
        });

        checkpoint("CP6", "抽检记录按池按批落库", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("pondId", pondId1);
            body.put("batchId", batchId);
            body.put("inspectDate", "2026-10-10");
            body.put("sampleCount", 50);
            body.put("avgWeightG", "3.20");
            body.put("survivalRate", "96.00");
            body.put("result", "长势正常");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/records/inspection", body));
            assertSuccess(resp, "抽检登记");
            List<InspectionRecord> rows = inspectionMapper.selectList(new QueryWrapper<InspectionRecord>()
                    .eq("pond_id", pondId1).eq("batch_id", batchId));
            check(rows.size() == 1, "t_inspection_record 按池按批查询行数应为1，实际=" + rows.size());
        });

        checkpoint("CP7", "换池形成转移记录并更新批次所在池", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("toPondId", pondId2);
            body.put("transferDate", "2026-10-15");
            body.put("reason", "分池稀养");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchId + "/transfer", body));
            assertSuccess(resp, "换池");

            List<TransferRecord> rows = transferMapper.selectList(
                    new QueryWrapper<TransferRecord>().eq("batch_id", batchId));
            check(rows.size() == 1, "t_transfer_record 应有1条转移记录，实际=" + rows.size());
            TransferRecord record = rows.get(0);
            check(record.getFromPondId().equals(pondId1), "转移记录来源池不符：" + record.getFromPondId());
            check(record.getToPondId().equals(pondId2), "转移记录目标池不符：" + record.getToPondId());
            check(Integer.valueOf(120000).equals(record.getQuantity()), "转移数量不符：" + record.getQuantity());

            SeedlingBatch batch = batchMapper.selectById(batchId);
            check(batch.getPondId().equals(pondId2), "换池后批次所在池未更新：" + batch.getPondId());
        });

        checkpoint("CP8", "原始投苗单不可删除", () -> {
            JsonNode resp = json(call(HttpMethod.DELETE, "/api/aqua/batches/" + batchId, null));
            check(!resp.path("success").asBoolean(true), "删除投苗单请求未被拒绝");
            SeedlingBatch row = batchMapper.selectById(batchId);
            check(row != null, "投苗单在库中丢失，id=" + batchId);
            check(batchNo.equals(row.getBatchNo()), "投苗单内容被篡改，batch_no=" + row.getBatchNo());
        });

        checkpoint("CP9", "出池：出池记录落库且批次封存", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("harvestDate", "2027-01-15");
            body.put("quantity", 112000);
            body.put("remark", "达到计划出池规格");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchId + "/harvest", body));
            assertSuccess(resp, "出池");

            List<HarvestRecord> rows = harvestMapper.selectList(
                    new QueryWrapper<HarvestRecord>().eq("batch_id", batchId));
            check(rows.size() == 1, "t_harvest_record 应有1条出池记录，实际=" + rows.size());
            check(rows.get(0).getPondId().equals(pondId2), "出池记录池号应为换池后的池：" + rows.get(0).getPondId());

            SeedlingBatch batch = batchMapper.selectById(batchId);
            check(SeedlingBatch.STATUS_HARVESTED.equals(batch.getStatus()),
                    "出池后批次状态应为已出池：" + batch.getStatus());
        });

        checkpoint("CP10", "出池后台账封存：禁止再登记饲料", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("pondId", pondId2);
            body.put("batchId", batchId);
            body.put("feedDate", "2027-01-16");
            body.put("feedName", "对虾配合饲料2号");
            body.put("quantityKg", "10.00");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/records/feed", body));
            check(!resp.path("success").asBoolean(true), "已出池批次仍能登记饲料，台账未封存");
            Long count = feedMapper.selectCount(new QueryWrapper<FeedRecord>().eq("batch_id", batchId));
            check(count != null && count == 1L, "封存后饲料记录行数应仍为1，实际=" + count);
        });

        report.add("验收结论：全部检查点通过，投苗批次已确认落库并可全程追溯");
        printReport();
    }

    private Long createPond(String code, String name, String seaArea, String depth, String salinity) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", seaArea);
        body.put("waterDepthM", depth);
        body.put("salinityPpt", salinity);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
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
        sb.append("\n================ 海水养殖台账验收报告 ================\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=====================================================\n");
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
