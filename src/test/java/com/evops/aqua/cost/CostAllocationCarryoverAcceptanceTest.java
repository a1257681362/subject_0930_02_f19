package com.evops.aqua.cost;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.entity.CostAdjustment;
import com.evops.aqua.cost.entity.CostBalance;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.mapper.CostAdjustmentMapper;
import com.evops.aqua.cost.mapper.CostBalanceMapper;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostChangeEvidenceMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.cost.service.CostReportService;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 养殖成本分摊与批次结转验收（aqua-10）。
 * 覆盖：四类费用按池批归集、人工补录必引凭证、转池按数量与生物量权重分配未结成本、
 * 出池形成不可变结转快照、已结转批次不能被新单据改写、差异调整待审核、
 * 财务看金额生产只看汇总、池塘置为无效后页面/导出/后台同口径且历史不删。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CostAllocationCarryoverAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private SeedlingBatchMapper batchMapper;
    @Autowired
    private CostEntryMapper entryMapper;
    @Autowired
    private CostBalanceMapper balanceMapper;
    @Autowired
    private CostTransferMapper transferMapper;
    @Autowired
    private CostTransferLineMapper transferLineMapper;
    @Autowired
    private CostCarryoverMapper carryoverMapper;
    @Autowired
    private CostCarryoverLineMapper carryoverLineMapper;
    @Autowired
    private CostAdjustmentMapper adjustmentMapper;
    @Autowired
    private CostChangeEvidenceMapper evidenceMapper;
    @Autowired
    private CostReportService reportService;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondA;
    private Long pondB;
    private Long pondC;
    private Long batch1;
    private Long childBatch;
    private Long batch2;
    private static final String B1_NO = "C10-B1";
    private static final String B2_NO = "C10-B2";

    @Test
    void acceptanceCostAllocationAndCarryover() {
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

        checkpoint("CP1", "建池并投苗（含初始生物量，作为后续分配权重）", () -> {
            pondA = createPond("C10-PA", "成本甲池", "2.00", "30.0");
            pondB = createPond("C10-PB", "成本乙池", "2.00", "30.0");
            pondC = createPond("C10-PC", "成本丙池", "2.00", "30.0");

            Map<String, Object> body = new HashMap<>();
            body.put("batchNo", B1_NO);
            body.put("pondId", pondA);
            body.put("species", "南美白对虾");
            body.put("seedSource", "C10育苗场");
            body.put("quantity", 1000);
            body.put("biomassKg", "100.000");
            body.put("stockDate", "2026-10-03");
            body.put("plannedHarvestDate", "2027-02-01");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
            assertSuccess(resp, "投苗");
            batch1 = resp.path("data").path("id").asLong();
            SeedlingBatch row = batchMapper.selectById(batch1);
            check(new BigDecimal("100.000").compareTo(row.getBiomassKg()) == 0,
                    "投苗生物量落库不符：" + row.getBiomassKg());
        });

        checkpoint("CP2", "财务凭证登记：无凭证/凭证不存在的人工补录一律拒绝", () -> {
            Map<String, Object> voucher = new HashMap<>();
            voucher.put("voucherNo", "C10-V1");
            voucher.put("voucherType", "PURCHASE");
            voucher.put("voucherDate", "2026-10-04");
            voucher.put("amount", "1000.00");
            voucher.put("summary", "饲料采购发票");
            voucher.put("requestId", "req-c10-voucher-1");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/vouchers", voucher)), "凭证登记");

            // 不传凭证号：参数校验失败
            Map<String, Object> noVoucher = entryBody(pondA, batch1, "FEED", "100.00", "2026-10-04", null, "req-c10-e-novoucher");
            JsonNode r1 = json(call(HttpMethod.POST, "/api/aqua/cost/entries", noVoucher));
            check(!r1.path("success").asBoolean(true), "无凭证号补录未被拦截");

            // 凭证不存在：业务拒绝
            Map<String, Object> badVoucher = entryBody(pondA, batch1, "FEED", "100.00", "2026-10-04", "NOT-EXIST-V", "req-c10-e-badvoucher");
            JsonNode r2 = json(call(HttpMethod.POST, "/api/aqua/cost/entries", badVoucher));
            check(!r2.path("success").asBoolean(true), "不存在凭证的补录未被拦截");
            check(r2.path("message").asText().contains("凭证"), "拒绝提示应说明凭证问题：" + r2.path("message").asText());

            Long entries = entryMapper.selectCount(new QueryWrapper<CostEntry>().eq("batch_id", batch1));
            check(entries == 0, "被拒补录不应产生费用凭条，实际行数=" + entries);
        });

        checkpoint("CP3", "饲料/人工/药品/能源按池按批归集（引用凭证），requestId 幂等", () -> {
            registerEntry(pondA, batch1, "FEED", "100.00", "2026-10-04", "C10-V1", "req-c10-e-feed");
            registerEntry(pondA, batch1, "LABOR", "80.00", "2026-10-04", "C10-V1", "req-c10-e-labor");
            registerEntry(pondA, batch1, "MED", "60.00", "2026-10-05", "C10-V1", "req-c10-e-med");
            registerEntry(pondA, batch1, "ENERGY", "40.00", "2026-10-05", "C10-V1", "req-c10-e-energy");

            // 重复 requestId 返回首次记录，不重复加账
            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, batch1, "FEED", "100.00", "2026-10-04", "C10-V1", "req-c10-e-feed")));
            assertSuccess(dup, "重复补录");
            Long feedCount = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                    .eq("batch_id", batch1).eq("cost_type", "FEED"));
            check(feedCount == 1L, "饲料凭条应仍为1条，实际=" + feedCount);

            check(amount(batch1, "FEED").compareTo(new BigDecimal("100.00")) == 0, "饲料余额不符");
            check(amount(batch1, "LABOR").compareTo(new BigDecimal("80.00")) == 0, "人工余额不符");
            check(amount(batch1, "MED").compareTo(new BigDecimal("60.00")) == 0, "药品余额不符");
            check(amount(batch1, "ENERGY").compareTo(new BigDecimal("40.00")) == 0, "能源余额不符");
        });

        checkpoint("CP4", "角色视图：财务看金额，生产只看到汇总（无金额无凭证）", () -> {
            JsonNode finance = json(call(HttpMethod.GET,
                    "/api/aqua/cost/allocation-report?role=FINANCE", null));
            assertSuccess(finance, "财务报表");
            JsonNode fRow = findRow(finance, B1_NO);
            check(new BigDecimal(fRow.path("totalAmount").asText()).compareTo(new BigDecimal("280.00")) == 0,
                    "财务视图合计金额应为280.00：" + fRow.path("totalAmount").asText());
            check(new BigDecimal(fRow.path("FEEDAmount").asText()).compareTo(BigDecimal.ONE) > 0,
                    "财务视图应含饲料金额列");

            JsonNode production = json(call(HttpMethod.GET,
                    "/api/aqua/cost/allocation-report?role=PRODUCTION", null));
            assertSuccess(production, "生产报表");
            JsonNode pRow = findRow(production, B1_NO);
            check(pRow.path("totalAmount").isMissingNode(), "生产视图不应下发合计金额");
            check(pRow.path("FEEDAmount").isMissingNode(), "生产视图不应下发饲料金额");
            check(!pRow.path("FEEDAmount").asText("").contains("100"), "生产视图泄露饲料金额");
            check(pRow.path("FEEDEntryCount").asInt() == 1, "生产视图应能看到饲料单据数汇总");
            check(!pRow.path("amountVisible").asBoolean(), "生产视图金额可见标记应为false");

            JsonNode detail = json(call(HttpMethod.GET,
                    "/api/aqua/cost/batches/" + batch1 + "?role=PRODUCTION", null));
            String detailText = detail.path("data").path("entries").toString();
            check(!detailText.contains("100.00") && !detailText.contains("C10-V1"),
                    "生产批次详情泄露金额或凭证号：" + detailText);

            // 凭条/凭证列表对生产角色同样脱敏
            JsonNode prodEntries = json(call(HttpMethod.GET,
                    "/api/aqua/cost/entries?batchId=" + batch1 + "&role=PRODUCTION", null));
            String prodEntryText = prodEntries.path("data").toString();
            check(!prodEntryText.contains("amount") && !prodEntryText.contains("voucherNo"),
                    "生产凭条列表泄露金额或凭证字段：" + prodEntryText);
            JsonNode finEntries = json(call(HttpMethod.GET,
                    "/api/aqua/cost/entries?batchId=" + batch1 + "&role=FINANCE", null));
            check(finEntries.path("data").get(0).path("amount").asText().length() > 0,
                    "财务凭条列表应包含金额");
            JsonNode prodVouchers = json(call(HttpMethod.GET,
                    "/api/aqua/cost/vouchers?role=PRODUCTION", null));
            check(!prodVouchers.path("data").toString().contains("amount"),
                    "生产凭证列表泄露金额字段");
            JsonNode prodEvidence = json(call(HttpMethod.GET,
                    "/api/aqua/cost/evidence?requestId=req-c10-e-feed&role=PRODUCTION", null));
            String prodEvText = prodEvidence.path("data").toString();
            check(!prodEvText.contains("afterJson") && !prodEvText.contains("100.00"),
                    "生产证据视图泄露快照或金额：" + prodEvText);
        });

        checkpoint("CP5", "整批换池：未结成本100%随批走，不重算金额、不产生新凭条", () -> {
            Map<String, Object> transfer = new HashMap<>();
            transfer.put("toPondId", pondB);
            transfer.put("transferDate", "2026-10-10");
            transfer.put("reason", "整池转移");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/transfer", transfer));
            assertSuccess(resp, "整批换池");

            SeedlingBatch batch = batchMapper.selectById(batch1);
            check(batch.getPondId().equals(pondB), "换池后批次应在乙池：" + batch.getPondId());
            check(amount(batch1, "FEED").compareTo(new BigDecimal("100.00")) == 0, "整批转移后饲料余额应不变");
            check(amount(batch1, "ENERGY").compareTo(new BigDecimal("40.00")) == 0, "整批转移后能源余额应不变");
            CostBalance feedBalance = getBalance(batch1, "FEED");
            check(feedBalance.getPondId().equals(pondB), "余额账应随批挪到乙池：" + feedBalance.getPondId());

            Long entries = entryMapper.selectCount(new QueryWrapper<CostEntry>().eq("batch_id", batch1));
            check(entries == 4L, "整批转移不应产生新凭条，凭条应仍为4条，实际=" + entries);

            CostTransfer ct = transferMapper.selectList(new QueryWrapper<CostTransfer>()
                    .eq("source_batch_id", batch1)).get(0);
            check(ct.getTotalAmount().compareTo(new BigDecimal("280.00")) == 0,
                    "转池成本单金额应为未结合计280.00：" + ct.getTotalAmount());
        });

        checkpoint("CP6", "分池：按数量与生物量权重（50%+50%）分配未结成本", () -> {
            // 乙池1000尾/100kg 分出 250尾/50kg 回甲池：数量权重0.25、生物量权重0.5 → 综合0.375
            Map<String, Object> split = new HashMap<>();
            split.put("toPondId", pondA);
            split.put("transferDate", "2026-10-12");
            split.put("quantity", 250);
            split.put("biomassKg", "50.000");
            split.put("reason", "分池稀养");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/transfer", split));
            assertSuccess(resp, "分池");

            SeedlingBatch source = batchMapper.selectById(batch1);
            check(source.getQuantity() == 750, "源批次留存数量应为750：" + source.getQuantity());
            check(source.getPondId().equals(pondB), "源批次应留在乙池");
            SeedlingBatch child = batchMapper.selectOne(new QueryWrapper<SeedlingBatch>()
                    .eq("split_parent_batch_id", batch1));
            check(child != null, "目标池应派生接收批次");
            check(child.getQuantity() == 250 && child.getPondId().equals(pondA),
                    "派生批应为250尾且在甲池");
            childBatch = child.getId();

            // 权重与分配额冻结
            CostTransferLine feedMoved = transferLineMapper.selectOne(new QueryWrapper<CostTransferLine>()
                    .eq("receive_batch_id", childBatch).eq("cost_type", "FEED"));
            check(feedMoved.getWeight().compareTo(new BigDecimal("0.375000")) == 0,
                    "分出综合权重应为0.375000：" + feedMoved.getWeight());
            check(feedMoved.getAllocatedAmount().compareTo(new BigDecimal("37.50")) == 0,
                    "饲料分出金额应为37.50：" + feedMoved.getAllocatedAmount());

            check(amount(batch1, "FEED").compareTo(new BigDecimal("62.50")) == 0, "源批饲料留存应为62.50");
            check(amount(childBatch, "FEED").compareTo(new BigDecimal("37.50")) == 0, "接收批饲料分得应为37.50");
            check(amount(batch1, "LABOR").compareTo(new BigDecimal("50.00")) == 0, "源批人工留存应为50.00");
            check(amount(childBatch, "LABOR").compareTo(new BigDecimal("30.00")) == 0, "接收批人工分得应为30.00");
            check(amount(childBatch, "MED").compareTo(new BigDecimal("22.50")) == 0, "接收批药品分得应为22.50");
            check(amount(childBatch, "ENERGY").compareTo(new BigDecimal("15.00")) == 0, "接收批能源分得应为15.00");

            CostBalance childBalance = getBalance(childBatch, "FEED");
            check(childBalance.getCarryoverInAmount().compareTo(new BigDecimal("37.50")) == 0,
                    "接收批累计转入金额应为37.50");
            CostBalance sourceBalance = getBalance(batch1, "FEED");
            check(sourceBalance.getCarryoverOutAmount().compareTo(new BigDecimal("37.50")) == 0,
                    "源批累计分出金额应为37.50");

            Long carryEntries = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                    .eq("batch_id", childBatch).eq("source", "CARRYOVER"));
            check(carryEntries == 4L, "接收批应有4条转池分配凭条（只追加可追溯），实际=" + carryEntries);
        });

        checkpoint("CP7", "出池形成结转快照，已结转批次不能被任何新单据改写", () -> {
            // 先出池甲池的派生批（结转 37.5+30+22.5+15 = 105.00）
            Map<String, Object> harvest = new HashMap<>();
            harvest.put("harvestDate", "2027-01-20");
            harvest.put("quantity", 240);
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + childBatch + "/harvest", harvest));
            assertSuccess(resp, "派生批出池");

            CostCarryover carryover = carryoverMapper.selectOne(
                    new QueryWrapper<CostCarryover>().eq("batch_id", childBatch));
            check(carryover != null, "出池后应形成结转快照");
            check(carryover.getTotalAmount().compareTo(new BigDecimal("105.00")) == 0,
                    "派生批结转总额应为105.00：" + carryover.getTotalAmount());
            check(carryover.getSnapshotJson() != null && carryover.getSnapshotJson().contains(B1_NO + "-S1"),
                    "快照应冻结批次信息");
            List<CostCarryoverLine> lines = carryoverLineMapper.selectList(
                    new QueryWrapper<CostCarryoverLine>().eq("carryover_id", carryover.getId()));
            check(lines.size() == 4, "快照应逐费用类型共4行，实际=" + lines.size());
            check(amount(childBatch, "FEED").compareTo(BigDecimal.ZERO) == 0,
                    "结转后在养余额必须清零");

            // 新费用单据不能改写
            JsonNode blockedEntry = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, childBatch, "FEED", "9.99", "2027-01-21", "C10-V1", "req-c10-after-h")));
            check(!blockedEntry.path("success").asBoolean(true), "已结转批次仍能补录费用，快照被改写");
            check(blockedEntry.path("message").asText().contains("结转"), "拒绝提示应说明已结转封冻");

            // 差异单也不能改已结转批次
            Map<String, Object> adj = adjustmentBody(childBatch, "FEED", "ADD", "9.99", "C10-V1", "req-c10-adj-locked");
            JsonNode blockedAdj = json(call(HttpMethod.POST, "/api/aqua/cost/adjustments", adj));
            check(!blockedAdj.path("success").asBoolean(true), "已结转批次仍能建差异单");
            // 快照金额原样
            CostCarryover again = carryoverMapper.selectOne(
                    new QueryWrapper<CostCarryover>().eq("batch_id", childBatch));
            check(again.getTotalAmount().compareTo(new BigDecimal("105.00")) == 0,
                    "快照金额被改写：" + again.getTotalAmount());
        });

        checkpoint("CP8", "源批次出池：留存成本全额结转（175.00），快照不可变", () -> {
            Map<String, Object> harvest = new HashMap<>();
            harvest.put("harvestDate", "2027-01-25");
            harvest.put("quantity", 700);
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/harvest", harvest)), "源批出池");

            CostCarryover carryover = carryoverMapper.selectOne(
                    new QueryWrapper<CostCarryover>().eq("batch_id", batch1));
            check(carryover.getTotalAmount().compareTo(new BigDecimal("175.00")) == 0,
                    "源批结转总额应为175.00（62.5+50+37.5+25）：" + carryover.getTotalAmount());
            for (String type : new String[]{"FEED", "LABOR", "MED", "ENERGY"}) {
                check(amount(batch1, type).compareTo(BigDecimal.ZERO) == 0,
                        "结转后" + type + "在养余额应清零");
            }
        });

        checkpoint("CP9", "差异调整必须待审核：通过才入账、驳回不动账、重复审核幂等", () -> {
            // 丙池新批次，归集饲料 200
            Map<String, Object> stock = new HashMap<>();
            stock.put("batchNo", B2_NO);
            stock.put("pondId", pondC);
            stock.put("species", "石斑鱼");
            stock.put("seedSource", "C10育苗场");
            stock.put("quantity", 500);
            stock.put("biomassKg", "50.000");
            stock.put("stockDate", "2026-10-06");
            stock.put("plannedHarvestDate", "2027-03-01");
            JsonNode s = json(call(HttpMethod.POST, "/api/aqua/batches", stock));
            assertSuccess(s, "丙池投苗");
            batch2 = s.path("data").path("id").asLong();
            registerEntry(pondC, batch2, "FEED", "200.00", "2026-10-07", "C10-V1", "req-c10-b2-feed");

            // 核减30差异单：建单后停留 PENDING，余额不动
            Map<String, Object> sub = adjustmentBody(batch2, "FEED", "SUB", "30.00", "C10-V1", "req-c10-adj-sub");
            JsonNode created = json(call(HttpMethod.POST, "/api/aqua/cost/adjustments", sub));
            assertSuccess(created, "差异建单");
            Long adjId = created.path("data").path("id").asLong();
            check("PENDING".equals(created.path("data").path("status").asText()), "差异单初始应为待审核");
            check(amount(batch2, "FEED").compareTo(new BigDecimal("200.00")) == 0,
                    "待审核差异不得动账，余额应仍为200.00");

            // 缺审核人/意见：参数校验失败，单停留 PENDING
            Map<String, Object> badReview = new HashMap<>();
            badReview.put("reviewedBy", "");
            badReview.put("reviewRemark", "");
            badReview.put("requestId", "req-c10-adj-badreview");
            JsonNode badResp = json(call(HttpMethod.POST,
                    "/api/aqua/cost/adjustments/" + adjId + "/approve", badReview));
            check(!badResp.path("success").asBoolean(true), "缺审核人的审核未被拦截");
            check("PENDING".equals(adjustmentMapper.selectById(adjId).getStatus()), "缺审核人时差异单不应离开待审核");

            // 审核通过：余额 200-30=170，补 ADJUSTMENT 凭条
            JsonNode approved = json(call(HttpMethod.POST, "/api/aqua/cost/adjustments/" + adjId + "/approve",
                    reviewBody("财务主管王五", "盘点差异属实，准予核减", "req-c10-adj-sub-ok")));
            assertSuccess(approved, "差异审核通过");
            check("APPROVED".equals(approved.path("data").path("status").asText()), "差异单状态应为已通过");
            check(amount(batch2, "FEED").compareTo(new BigDecimal("170.00")) == 0,
                    "审核通过后余额应为170.00：" + amount(batch2, "FEED"));
            Long adjEntries = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                    .eq("adjustment_id", adjId));
            check(adjEntries == 1L, "审核通过应补1条差异入账凭条，实际=" + adjEntries);

            // 重复审核幂等：不多入账
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/cost/adjustments/" + adjId + "/approve",
                    reviewBody("财务主管王五", "重复审核", "req-c10-adj-sub-ok2")));
            assertSuccess(again, "重复审核");
            check(amount(batch2, "FEED").compareTo(new BigDecimal("170.00")) == 0,
                    "重复审核后余额应仍为170.00");
            check(entryMapper.selectCount(new QueryWrapper<CostEntry>().eq("adjustment_id", adjId)) == 1L,
                    "重复审核不应多补凭条");

            // 另一单调增50被驳回：余额保持170
            Map<String, Object> add = adjustmentBody(batch2, "FEED", "ADD", "50.00", "C10-V1", "req-c10-adj-add");
            JsonNode addCreated = json(call(HttpMethod.POST, "/api/aqua/cost/adjustments", add));
            Long addId = addCreated.path("data").path("id").asLong();
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/adjustments/" + addId + "/reject",
                    reviewBody("财务主管王五", "依据不足，驳回", "req-c10-adj-add-no"))), "差异驳回");
            check(amount(batch2, "FEED").compareTo(new BigDecimal("170.00")) == 0,
                    "驳回差异不得动账，余额应仍为170.00");
            check("REJECTED".equals(adjustmentMapper.selectById(addId).getStatus()), "差异单状态应为已驳回");
        });

        checkpoint("CP10", "池塘置为无效：页面、导出、后台同口径，历史凭条与快照不删", () -> {
            // 停用前：默认口径三批都在
            Set<String> before = reportBatchNos(reportService.loadRows(false));
            check(before.contains(B1_NO) && before.contains(B1_NO + "-S1") && before.contains(B2_NO),
                    "停用前报表应含全部批次：" + before);

            Map<String, Object> disable = new HashMap<>();
            disable.put("reason", "乙池退养还海");
            disable.put("operator", "场长赵六");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds/" + pondB + "/disable", disable));
            assertSuccess(resp, "池塘停用");
            check("DISABLED".equals(pondMapper.selectById(pondB).getStatus()), "池塘状态应为无效");

            // 后台口径：聚合直接缺乙池批次
            Set<String> backend = reportBatchNos(reportService.loadRows(false));
            check(!backend.contains(B1_NO), "后台聚合仍统计已停用乙池批次，口径不一致：" + backend);
            check(backend.contains(B1_NO + "-S1") && backend.contains(B2_NO),
                    "甲/丙池有效批次仍应在后台聚合中：" + backend);

            // 页面对账：财务与生产两种视图同口径
            JsonNode financePage = json(call(HttpMethod.GET,
                    "/api/aqua/cost/allocation-report?role=FINANCE", null));
            Set<String> pageFinance = batchNosFromPage(financePage);
            JsonNode prodPage = json(call(HttpMethod.GET,
                    "/api/aqua/cost/allocation-report?role=PRODUCTION", null));
            Set<String> pageProd = batchNosFromPage(prodPage);
            check(pageFinance.equals(backend), "财务页面口径与后台不一致：" + pageFinance + " vs " + backend);
            check(pageProd.equals(backend), "生产页面口径与后台不一致：" + pageProd + " vs " + backend);

            // 导出对账：CSV 批次集合与页面/后台一致（财务导出含金额列、生产导出无金额列）
            ResponseEntity<String> csvFinance = call(HttpMethod.GET,
                    "/api/aqua/cost/export?role=FINANCE", null);
            ResponseEntity<String> csvProd = call(HttpMethod.GET,
                    "/api/aqua/cost/export?role=PRODUCTION", null);
            Set<String> csvFinanceBatches = batchNosFromCsv(csvFinance.getBody());
            Set<String> csvProdBatches = batchNosFromCsv(csvProd.getBody());
            check(csvFinanceBatches.equals(backend), "财务导出口径与后台不一致：" + csvFinanceBatches + " vs " + backend);
            check(csvProdBatches.equals(backend), "生产导出口径与后台不一致：" + csvProdBatches + " vs " + backend);
            check(csvFinance.getBody().contains("合计金额"), "财务导出应含合计金额列");
            check(!csvProd.getBody().contains("合计金额"), "生产导出不得含金额列");
            check(!csvProd.getBody().contains("170.00") && !csvProd.getBody().contains("C10-V1"),
                    "生产导出不得含金额或凭证数据");
            check(!csvProdBatches.contains(B1_NO) && !csvFinanceBatches.contains(B1_NO),
                    "导出仍出现停用池批次" + B1_NO + "（按批次列精确比对）");

            // 显式放开 includeDisabled=true：三口径仍一致且重新含乙池批次
            Set<String> withDisabled = reportBatchNos(reportService.loadRows(true));
            check(withDisabled.contains(B1_NO), "显式放开时后台应能查到停用池历史批次");
            JsonNode allPage = json(call(HttpMethod.GET,
                    "/api/aqua/cost/allocation-report?role=FINANCE&includeDisabled=true", null));
            check(batchNosFromPage(allPage).equals(withDisabled), "显式放开页面口径与后台不一致");

            // 历史不删：费用凭条、结转快照、证据全部还在
            check(carryoverMapper.selectCount(new QueryWrapper<CostCarryover>().eq("batch_id", batch1)) == 1L,
                    "停用后源批结转快照丢失");
            check(entryMapper.selectCount(new QueryWrapper<CostEntry>().eq("batch_id", batch1)) > 0,
                    "停用后乙池历史费用凭条被删除");
            Long disableEvidence = evidenceMapper.selectCount(new QueryWrapper<CostChangeEvidence>()
                    .eq("entity_type", "POND").eq("entity_id", pondB)
                    .eq("action", "DISABLE"));
            check(disableEvidence == 1L, "池塘停用应留1条证据，实际=" + disableEvidence);
        });

        checkpoint("CP11", "全程留证：归集/分配/结转/差异审核/停用均可按对象或 requestId 追溯", () -> {
            check(!evidenceServiceEmpty("COST_ENTRY", null, "req-c10-e-feed"), "饲料归集证据缺失");
            check(!evidenceServiceEmpty("COST_TRANSFER", null, null), "转池分配证据缺失");
            check(!evidenceServiceEmpty("COST_CARRYOVER", null, null), "出池结转证据缺失");
            check(!evidenceServiceEmpty("COST_ADJUSTMENT", null, "req-c10-adj-sub-ok"), "差异审核证据缺失");
            JsonNode list = json(call(HttpMethod.GET,
                    "/api/aqua/cost/evidence?requestId=req-c10-e-feed", null));
            assertSuccess(list, "证据查询");
            check(list.path("data").size() >= 1, "按 requestId 查不到归集证据");
        });

        report.add("验收结论：全部检查点通过，养殖成本分摊与批次结转口径一致、封冻可靠");
        printReport();
    }

    // ============================================================
    // 辅助
    // ============================================================

    private boolean evidenceServiceEmpty(String entityType, Long entityId, String requestId) {
        return evidenceMapper.selectCount(new QueryWrapper<CostChangeEvidence>()
                .eq(entityType != null, "entity_type", entityType)
                .eq(entityId != null, "entity_id", entityId)
                .eq(requestId != null, "request_id", requestId)) == 0;
    }

    private Set<String> reportBatchNos(List<Map<String, Object>> rows) {
        Set<String> nos = new HashSet<>();
        for (Map<String, Object> row : rows) {
            nos.add((String) row.get("batchNo"));
        }
        return nos;
    }

    private Set<String> batchNosFromPage(JsonNode page) {
        Set<String> nos = new HashSet<>();
        JsonNode rows = page.path("data").path("rows");
        for (JsonNode row : rows) {
            nos.add(row.path("batchNo").asText());
        }
        return nos;
    }

    private Set<String> batchNosFromCsv(String csv) {
        Set<String> nos = new HashSet<>();
        String[] lines = csv.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                nos.add(lines[i].split(",", 2)[0]);
            }
        }
        return nos;
    }

    private JsonNode findRow(JsonNode page, String batchNo) throws Exception {
        JsonNode rows = page.path("data").path("rows");
        for (JsonNode row : rows) {
            if (batchNo.equals(row.path("batchNo").asText())) {
                return row;
            }
        }
        throw new IllegalStateException("报表中找不到批次：" + batchNo);
    }

    private BigDecimal amount(Long batchId, String type) {
        CostBalance balance = getBalance(batchId, type);
        return balance == null ? BigDecimal.ZERO : balance.getSettledAmount();
    }

    private CostBalance getBalance(Long batchId, String type) {
        return balanceMapper.selectOne(new QueryWrapper<CostBalance>()
                .eq("batch_id", batchId).eq("cost_type", type));
    }

    private void registerEntry(Long pondId, Long batchId, String type, String amount, String date,
                               String voucherNo, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                entryBody(pondId, batchId, type, amount, date, voucherNo, requestId)));
        assertSuccess(resp, type + "费用归集");
    }

    private Map<String, Object> entryBody(Long pondId, Long batchId, String type, String amount,
                                          String date, String voucherNo, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        body.put("batchId", batchId);
        body.put("costType", type);
        body.put("amount", amount);
        body.put("occurDate", date);
        body.put("voucherNo", voucherNo);
        body.put("operator", "成本会计");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> adjustmentBody(Long batchId, String type, String direction, String amount,
                                               String voucherNo, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchId", batchId);
        body.put("costType", type);
        body.put("direction", direction);
        body.put("amount", amount);
        body.put("occurDate", "2026-10-20");
        body.put("voucherNo", voucherNo);
        body.put("reason", "盘点发现饲料账面差异");
        body.put("applicant", "生产主管李四");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> reviewBody(String by, String remark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("reviewedBy", by);
        body.put("reviewRemark", remark);
        body.put("requestId", requestId);
        return body;
    }

    private Long createPond(String code, String name, String depth, String salinity) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", "C10湾海区");
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
        sb.append("\n================ 养殖成本分摊与批次结转验收报告 ================\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==============================================================\n");
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
