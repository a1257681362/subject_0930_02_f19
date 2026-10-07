package com.evops.aqua.med;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.med.entity.MedBatch;
import com.evops.aqua.med.entity.MedChangeEvidence;
import com.evops.aqua.med.entity.MedRequisition;
import com.evops.aqua.med.entity.MedWithdrawalLock;
import com.evops.aqua.med.mapper.MedBatchMapper;
import com.evops.aqua.med.mapper.MedChangeEvidenceMapper;
import com.evops.aqua.med.mapper.MedRequisitionItemMapper;
import com.evops.aqua.med.mapper.MedRequisitionMapper;
import com.evops.aqua.med.mapper.MedWithdrawalLockMapper;
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
 * 养殖用药管理验收（aqua-6）。
 * 主线：药品档案与批号入库（幂等）→ 领用单关联池塘/病情/批号并按剂量×水体估算用量
 * → 提交后计算休药期并锁定预计出池日（多药叠加取最晚结束日）→ 休药期内禁止出池
 * → 审批字段不全停留待处理 → 库存扣减与领用审核同一事务（不足则整体停留）
 * → 撤回只能撤销未审核单 → 兽医看处方明细、仓管只看库存字段 → 全程留证可追溯。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedRequisitionAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SeedlingBatchMapper seedlingBatchMapper;
    @Autowired
    private MedRequisitionMapper requisitionMapper;
    @Autowired
    private MedRequisitionItemMapper itemMapper;
    @Autowired
    private MedBatchMapper medBatchMapper;
    @Autowired
    private MedWithdrawalLockMapper lockMapper;
    @Autowired
    private MedChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pond1;      // 面积2000㎡ × 水深1.5m = 3000m³
    private Long pond2;      // 面积1000㎡ × 水深2.0m = 2000m³
    private Long batch1;
    private Long batch2;
    private Long obs1;
    private Long drugA;      // 剂量2 g/m³，休药期20天
    private Long drugB;      // 剂量1.5 g/m³，休药期35天
    private Long medBatchA1; // 10000g，够用
    private Long medBatchB1; // 10000g，够用
    private Long medBatchB2; // 100g，故意不足
    private Long req1;
    private Long req2;

    @Test
    void acceptanceMedicationRequisition() {
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

        checkpoint("CP1", "建池（含水面面积）与投苗", () -> {
            pond1 = createPond("MED-P1", "用药一号池", "2000", "1.50");
            pond2 = createPond("MED-P2", "用药二号池", "1000", "2.00");
            batch1 = stockBatch("MED-C01", pond1);
            batch2 = stockBatch("MED-C02", pond2);
        });

        checkpoint("CP2", "登记药品档案：剂量与休药期；requestId幂等；缺操作者被拒", () -> {
            Map<String, Object> noOperator = drugBody("DRUG-A", "恩诺沙星", "2", "20", null, "drug-x");
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/med/drugs", noOperator));
            check(!denied.path("success").asBoolean(true), "缺操作者登记药品应被参数校验拒绝");

            JsonNode a = json(call(HttpMethod.POST, "/api/aqua/med/drugs",
                    drugBody("DRUG-A", "恩诺沙星", "2", "20", "药政员", "drug-a")));
            assertSuccess(a, "登记药品A");
            drugA = a.path("data").path("id").asLong();
            JsonNode b = json(call(HttpMethod.POST, "/api/aqua/med/drugs",
                    drugBody("DRUG-B", "氟苯尼考", "1.5", "35", "药政员", "drug-b")));
            assertSuccess(b, "登记药品B");
            drugB = b.path("data").path("id").asLong();

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/med/drugs",
                    drugBody("DRUG-A", "恩诺沙星", "2", "20", "药政员", "drug-a")));
            assertSuccess(again, "重复登记药品A");
            check(again.path("data").path("id").asLong() == drugA, "同requestId重复登记应返回同一药品");
        });

        checkpoint("CP3", "药品批号入库：A1/B1库存充足，B2仅100故意不足；幂等", () -> {
            medBatchA1 = stockInBatch("BA-2026-01", drugA, "10000", "2027-06-01", "in-a1");
            medBatchB1 = stockInBatch("BB-2026-01", drugB, "10000", "2027-06-01", "in-b1");
            medBatchB2 = stockInBatch("BB-2026-02", drugB, "100", "2027-06-01", "in-b2");
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/med/batches",
                    batchBody("BA-2026-01", drugA, "10000", "2027-06-01", "in-a1")));
            assertSuccess(again, "重复入库");
            check(again.path("data").path("id").asLong() == medBatchA1, "同requestId重复入库应返回同一批号");
            check(medBatchMapper.selectCount(new QueryWrapper<MedBatch>()
                    .eq("batch_no", "BA-2026-01")) == 1L, "幂等入库不得产生第二条批号");
        });

        checkpoint("CP4", "病情：一号池发现异常症状生成观察单", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("pondId", pond1);
            body.put("batchId", batch1);
            body.put("observeDate", "2026-10-08");
            body.put("symptoms", "体表溃疡、摄食骤降");
            body.put("riskLevel", "HIGH");
            body.put("observer", "张巡塘");
            body.put("requestId", "med-obs-1");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/disease/observations", body));
            assertSuccess(resp, "生成观察单");
            obs1 = resp.path("data").path("id").asLong();
        });

        checkpoint("CP5", "创建领用草稿：关联池塘/病情/批号，按剂量×水体估算用量；跨池病情被拒；幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/med/requisitions",
                    reqBody(pond1, obs1, "2026-10-10", "申请人林养", "req-1",
                            item(drugA, medBatchA1, null), item(drugB, medBatchB1, null))));
            assertSuccess(resp, "创建领用单1");
            req1 = resp.path("data").path("id").asLong();
            check("DRAFT".equals(resp.path("data").path("status").asText()), "新领用单应为草稿");
            check(new BigDecimal("3000.00").compareTo(
                    new BigDecimal(resp.path("data").path("waterVolumeM3").asText())) == 0,
                    "水体应为面积×水深=3000m³，实际=" + resp.path("data").path("waterVolumeM3").asText());

            JsonNode vet = json(call(HttpMethod.GET, "/api/aqua/med/requisitions/" + req1 + "?role=VET", null));
            JsonNode items = vet.path("data").path("items");
            check(items.size() == 2, "明细应为两行");
            check(new BigDecimal("6000.00").compareTo(
                    new BigDecimal(items.get(0).path("estimatedQty").asText())) == 0,
                    "药品A估算量应为2×3000=6000，实际=" + items.get(0).path("estimatedQty").asText());
            check(new BigDecimal("4500.00").compareTo(
                    new BigDecimal(items.get(1).path("estimatedQty").asText())) == 0,
                    "药品B估算量应为1.5×3000=4500，实际=" + items.get(1).path("estimatedQty").asText());

            Map<String, Object> wrongPond = reqBody(pond2, obs1, "2026-10-10", "申请人林养", "req-x1",
                    item(drugA, medBatchA1, null));
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/med/requisitions", wrongPond));
            check(!denied.path("success").asBoolean(true)
                            && denied.path("message").asText().contains("不一致"),
                    "观察单与领用池塘不一致应被拒绝：" + denied.path("message").asText());

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/med/requisitions",
                    reqBody(pond1, obs1, "2026-10-10", "申请人林养", "req-1",
                            item(drugA, medBatchA1, null), item(drugB, medBatchB1, null))));
            assertSuccess(again, "重复创建领用单1");
            check(again.path("data").path("id").asLong() == req1, "同requestId重复创建应返回同一领用单");
            check(requisitionMapper.selectCount(new QueryWrapper<MedRequisition>()
                    .eq("pond_id", pond1)) == 1L, "幂等创建不得产生第二张领用单");
        });

        checkpoint("CP6", "提交：计算休药期并锁定预计出池日，多药叠加取最晚结束日", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/submit",
                    actionBody("申请人林养", "submit-1")));
            assertSuccess(resp, "提交领用单1");
            check("PENDING".equals(resp.path("data").path("status").asText()), "提交后应为待审核");

            List<com.evops.aqua.med.entity.MedRequisitionItem> items = itemMapper.selectList(
                    new QueryWrapper<com.evops.aqua.med.entity.MedRequisitionItem>()
                            .eq("requisition_id", req1).orderByAsc("id"));
            check("2026-10-30".equals(items.get(0).getWithdrawalEndDate().toString()),
                    "药品A休药结束日应为10-10+20天=10-30，实际=" + items.get(0).getWithdrawalEndDate());
            check("2026-11-14".equals(items.get(1).getWithdrawalEndDate().toString()),
                    "药品B休药结束日应为10-10+35天=11-14，实际=" + items.get(1).getWithdrawalEndDate());

            MedWithdrawalLock lock = lockMapper.selectOne(new QueryWrapper<MedWithdrawalLock>()
                    .eq("pond_id", pond1));
            check(lock != null && "2026-11-14".equals(lock.getLockedUntil().toString()),
                    "多药叠加应锁定最晚结束日2026-11-14，实际=" + (lock == null ? "无锁" : lock.getLockedUntil()));

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/submit",
                    actionBody("申请人林养", "submit-1")));
            assertSuccess(again, "重复提交");
            check("PENDING".equals(again.path("data").path("status").asText()), "重复提交应保持待审核");
        });

        checkpoint("CP7", "休药期内禁止出池：预计出池日不早于锁定日", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("harvestDate", "2026-11-01");
            body.put("quantity", 50000);
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/harvest", body));
            check(!resp.path("success").asBoolean(true)
                            && resp.path("message").asText().contains("休药期"),
                    "休药期内出池应被拒绝并说明原因：" + resp.path("message").asText());
            check(resp.path("message").asText().contains("2026-11-14"),
                    "拒绝原因应给出最早可出池日2026-11-14：" + resp.path("message").asText());
            check(SeedlingBatch.STATUS_STOCKED.equals(seedlingBatchMapper.selectById(batch1).getStatus()),
                    "批次状态应保持不变");
        });

        checkpoint("CP8", "审批只完成一半：缺审批人或缺意见一律拒绝，领用单停留待处理且库存不动", () -> {
            JsonNode r1 = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/approve",
                    reviewBody(null, "同意领用", "apv-x1")));
            check(!r1.path("success").asBoolean(true), "缺审批人应被拒绝");
            JsonNode r2 = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/approve",
                    reviewBody("陈兽医", null, "apv-x2")));
            check(!r2.path("success").asBoolean(true), "缺审批意见应被拒绝");
            check(MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(req1).getStatus()),
                    "审批不全时领用单应停留待审核");
            check(availableOf(medBatchA1).compareTo(new BigDecimal("10000")) == 0
                            && availableOf(medBatchB1).compareTo(new BigDecimal("10000")) == 0,
                    "审批不全不得扣减任何库存");
        });

        checkpoint("CP9", "审批通过：库存扣减与领用审核同一事务；重复审批幂等不多扣", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/approve",
                    reviewBody("陈兽医", "症状明确，同意按方领用", "apv-1")));
            assertSuccess(resp, "审批通过");
            check("APPROVED".equals(resp.path("data").path("status").asText()), "应转为已审核");
            check("陈兽医".equals(resp.path("data").path("approvedBy").asText())
                            && resp.path("data").path("approvedTime") != null
                            && !resp.path("data").path("approvedTime").isNull(),
                    "审批人与审批时间必须落库");
            check(availableOf(medBatchA1).compareTo(new BigDecimal("4000")) == 0,
                    "A1应扣减6000余4000，实际=" + availableOf(medBatchA1));
            check(availableOf(medBatchB1).compareTo(new BigDecimal("5500")) == 0,
                    "B1应扣减4500余5500，实际=" + availableOf(medBatchB1));

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/approve",
                    reviewBody("陈兽医", "症状明确，同意按方领用", "apv-1")));
            assertSuccess(again, "重复审批");
            check(availableOf(medBatchA1).compareTo(new BigDecimal("4000")) == 0
                            && availableOf(medBatchB1).compareTo(new BigDecimal("5500")) == 0,
                    "重复审批不得多扣库存");
        });

        checkpoint("CP10", "已审核单不可撤回；休药期结束后出池放行", () -> {
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req1 + "/withdraw",
                    actionBody("申请人林养", "wd-x1")));
            check(!denied.path("success").asBoolean(true)
                            && denied.path("message").asText().contains("不可撤回"),
                    "已审核单撤回应被拒绝：" + denied.path("message").asText());
            check(MedRequisition.STATUS_APPROVED.equals(requisitionMapper.selectById(req1).getStatus()),
                    "已审核单状态应保持不变");

            Map<String, Object> body = new HashMap<>();
            body.put("harvestDate", "2026-11-15");
            body.put("quantity", 50000);
            JsonNode harvest = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch1 + "/harvest", body));
            assertSuccess(harvest, "休药期结束后出池");
            check(SeedlingBatch.STATUS_HARVESTED.equals(seedlingBatchMapper.selectById(batch1).getStatus()),
                    "批次应转为已出池");
        });

        checkpoint("CP11", "库存不足时审核整体失败：停留待处理、失败留证、库存零扣减", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/med/requisitions",
                    reqBody(pond2, null, "2026-10-10", "申请人林养", "req-2",
                            item(drugB, medBatchB2, null))));
            assertSuccess(resp, "创建领用单2");
            req2 = resp.path("data").path("id").asLong();
            check(new BigDecimal("2000.00").compareTo(
                    new BigDecimal(resp.path("data").path("waterVolumeM3").asText())) == 0,
                    "二号池水体应为2000m³");

            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req2 + "/submit",
                    actionBody("申请人林养", "submit-2"))), "提交领用单2");
            MedWithdrawalLock lock = lockMapper.selectOne(new QueryWrapper<MedWithdrawalLock>()
                    .eq("pond_id", pond2));
            check(lock != null && "2026-11-14".equals(lock.getLockedUntil().toString()),
                    "二号池应锁定至2026-11-14");

            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req2 + "/approve",
                    reviewBody("陈兽医", "同意领用", "apv-2")));
            check(!denied.path("success").asBoolean(true)
                            && denied.path("message").asText().contains("库存不足"),
                    "库存不足的审核应整体失败：" + denied.path("message").asText());
            check(MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(req2).getStatus()),
                    "审核失败后领用单应停留待审核");
            check(availableOf(medBatchB2).compareTo(new BigDecimal("100")) == 0,
                    "审核失败不得扣减任何库存，实际=" + availableOf(medBatchB2));
            List<MedChangeEvidence> failed = evidenceMapper.selectList(new QueryWrapper<MedChangeEvidence>()
                    .eq("action", MedChangeEvidence.ACTION_REQ_APPROVE_FAILED).eq("entity_id", req2));
            check(failed.size() == 1 && "陈兽医".equals(failed.get(0).getOperator())
                            && failed.get(0).getDetail().contains("库存不足"),
                    "审核失败应独立事务留证且带操作者与原因");
        });

        checkpoint("CP12", "撤回只能撤销未审核单：撤回成功并释放休药锁定，出池放行", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/med/requisitions/" + req2 + "/withdraw",
                    actionBody("申请人林养", "wd-2")));
            assertSuccess(resp, "撤回未审核单");
            check("WITHDRAWN".equals(resp.path("data").path("status").asText()), "应转为已撤回");
            check("申请人林养".equals(resp.path("data").path("withdrawBy").asText()),
                    "撤回人必须落库");
            check(lockMapper.selectCount(new QueryWrapper<MedWithdrawalLock>().eq("pond_id", pond2)) == 0L,
                    "撤回后二号池休药锁定应释放");

            Map<String, Object> body = new HashMap<>();
            body.put("harvestDate", "2026-10-20");
            body.put("quantity", 30000);
            JsonNode harvest = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch2 + "/harvest", body));
            assertSuccess(harvest, "锁定释放后出池");
        });

        checkpoint("CP13", "角色视图：兽医看处方明细，仓管只看库存字段", () -> {
            JsonNode vet = json(call(HttpMethod.GET, "/api/aqua/med/requisitions/" + req1 + "?role=VET", null));
            assertSuccess(vet, "兽医视图");
            check(vet.path("data").path("diseaseDesc").asText().contains("溃疡"),
                    "兽医应看到病情描述");
            check(!vet.path("data").path("items").get(0).path("dosagePerM3").isMissingNode()
                            && !vet.path("data").path("items").get(0).path("withdrawalEndDate").isMissingNode(),
                    "兽医应看到剂量与休药结束日");

            JsonNode keeper = json(call(HttpMethod.GET, "/api/aqua/med/requisitions/" + req1 + "?role=KEEPER", null));
            assertSuccess(keeper, "仓管视图");
            check(keeper.path("data").path("diseaseDesc").isMissingNode()
                            && keeper.path("data").path("observationId").isMissingNode(),
                    "仓管不得看到病情与观察单信息");
            JsonNode kItems = keeper.path("data").path("items");
            check(kItems.size() == 2
                            && !kItems.get(0).path("batchNo").isMissingNode()
                            && !kItems.get(0).path("estimatedQty").isMissingNode(),
                    "仓管应看到批号与数量等库存字段");
            check(kItems.get(0).path("dosagePerM3").isMissingNode()
                            && kItems.get(0).path("withdrawalDays").isMissingNode()
                            && kItems.get(0).path("withdrawalEndDate").isMissingNode(),
                    "仓管不得看到剂量与休药期等处方字段");
        });

        checkpoint("CP14", "全程留证：库存与领用单均可追溯，每条证据带操作者", () -> {
            check(evidenceCount(MedChangeEvidence.ACTION_DRUG_CREATE, null) == 2, "药品登记证据应为2条");
            check(evidenceCount(MedChangeEvidence.ACTION_BATCH_IN, null) == 3, "批号入库证据应为3条");
            check(evidenceCount(MedChangeEvidence.ACTION_REQ_CREATE, null) == 2, "领用创建证据应为2条");
            check(evidenceCount(MedChangeEvidence.ACTION_REQ_SUBMIT, null) == 2, "领用提交证据应为2条");
            check(evidenceCount(MedChangeEvidence.ACTION_REQ_APPROVE, req1) == 1, "审核通过证据缺失");
            check(evidenceCount(MedChangeEvidence.ACTION_REQ_APPROVE_FAILED, req2) == 1, "审核失败证据缺失");
            check(evidenceCount(MedChangeEvidence.ACTION_REQ_WITHDRAW, req2) == 1, "撤回证据缺失");
            check(evidenceCount(MedChangeEvidence.ACTION_STOCK_DEDUCT, medBatchA1) == 1
                            && evidenceCount(MedChangeEvidence.ACTION_STOCK_DEDUCT, medBatchB1) == 1,
                    "库存扣减证据应逐批号各一条");
            check(evidenceCount(MedChangeEvidence.ACTION_LOCK_UPDATE, null) >= 2
                            && evidenceCount(MedChangeEvidence.ACTION_LOCK_RELEASE, null) == 1,
                    "休药锁定/释放证据缺失");
            List<MedChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            check(!all.isEmpty() && all.stream().allMatch(e -> e.getOperator() != null
                            && !e.getOperator().trim().isEmpty()),
                    "每条证据都必须带操作者");

            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/med/evidence?entityType=MED_REQUISITION&entityId=" + req1, null));
            assertSuccess(resp, "按领用单追溯证据");
            List<String> actions = new ArrayList<>();
            resp.path("data").forEach(e -> actions.add(e.path("action").asText()));
            check(actions.containsAll(Arrays.asList("REQ_CREATE", "REQ_SUBMIT", "REQ_APPROVE")),
                    "领用单1应可追溯到创建/提交/审核全链路，实际=" + actions);
        });

        report.add("验收结论：全部检查点通过——领用单关联池塘/病情/批号并按剂量×水体估算用量、提交后计算休药期并锁定预计出池日（多药取最晚）、休药期内禁止出池、审批不全停留待处理、库存扣减与审核同事务、撤回仅限未审核单、兽医/仓管分角色视图、全程留证可追溯");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name, String waterAreaM2, String waterDepthM) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", "东山湾海区");
        body.put("waterDepthM", waterDepthM);
        body.put("waterAreaM2", waterAreaM2);
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockBatch(String batchNo, Long pondId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", "LARGE_YELLOW_CROAKER");
        body.put("seedSource", "东山湾苗种场");
        body.put("quantity", 50000);
        body.put("stockDate", "2026-10-01");
        body.put("plannedHarvestDate", "2027-03-01");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> drugBody(String code, String name, String dosage, String withdrawalDays,
                                         String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("drugCode", code);
        body.put("drugName", name);
        body.put("spec", "100g/袋");
        body.put("unit", "g");
        body.put("dosagePerM3", dosage);
        body.put("withdrawalDays", withdrawalDays);
        if (operator != null) {
            body.put("operator", operator);
        }
        body.put("requestId", requestId);
        return body;
    }

    private Long stockInBatch(String batchNo, Long drugId, String quantity,
                              String expiryDate, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/med/batches",
                batchBody(batchNo, drugId, quantity, expiryDate, requestId)));
        assertSuccess(resp, "批号入库[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> batchBody(String batchNo, Long drugId, String quantity,
                                          String expiryDate, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("drugId", drugId);
        body.put("supplier", "闽东兽药经营部");
        body.put("quantityIn", quantity);
        body.put("productionDate", "2026-06-01");
        body.put("expiryDate", expiryDate);
        body.put("operator", "仓管员王库");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> item(Long drugId, Long medBatchId, String dosagePerM3) {
        Map<String, Object> item = new HashMap<>();
        item.put("drugId", drugId);
        item.put("medBatchId", medBatchId);
        if (dosagePerM3 != null) {
            item.put("dosagePerM3", dosagePerM3);
        }
        return item;
    }

    private Map<String, Object> reqBody(Long pondId, Long observationId, String useDate,
                                        String applicant, String requestId, Map<String, Object>... items) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        if (observationId != null) {
            body.put("observationId", observationId);
        }
        body.put("diseaseDesc", "体表溃疡、摄食骤降，疑似细菌性感染");
        body.put("useDate", useDate);
        body.put("applicant", applicant);
        body.put("requestId", requestId);
        body.put("items", Arrays.asList(items));
        return body;
    }

    private Map<String, Object> actionBody(String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> reviewBody(String approvedBy, String approveRemark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        if (approvedBy != null) {
            body.put("approvedBy", approvedBy);
        }
        if (approveRemark != null) {
            body.put("approveRemark", approveRemark);
        }
        body.put("requestId", requestId);
        return body;
    }

    private BigDecimal availableOf(Long medBatchId) {
        return medBatchMapper.selectById(medBatchId).availableQuantity();
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<MedChangeEvidence> wrapper = new QueryWrapper<MedChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long c = evidenceMapper.selectCount(wrapper);
        return c == null ? 0L : c;
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
        sb.append("\n========== 养殖用药管理验收报告 ==========\n");
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
