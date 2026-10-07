package com.evops.aqua.med;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.dto.PondCreateRequest;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.med.dto.MedBatchInRequest;
import com.evops.aqua.med.dto.MedDrugRequest;
import com.evops.aqua.med.dto.RequisitionActionRequest;
import com.evops.aqua.med.dto.RequisitionCreateRequest;
import com.evops.aqua.med.dto.RequisitionItemRequest;
import com.evops.aqua.med.dto.RequisitionResubmitRequest;
import com.evops.aqua.med.dto.RequisitionReviewRequest;
import com.evops.aqua.med.entity.MedBatch;
import com.evops.aqua.med.entity.MedChangeEvidence;
import com.evops.aqua.med.entity.MedRequisition;
import com.evops.aqua.med.entity.MedRequisitionItem;
import com.evops.aqua.med.entity.MedWithdrawalLock;
import com.evops.aqua.med.mapper.MedBatchMapper;
import com.evops.aqua.med.mapper.MedChangeEvidenceMapper;
import com.evops.aqua.med.mapper.MedRequisitionItemMapper;
import com.evops.aqua.med.mapper.MedRequisitionMapper;
import com.evops.aqua.med.mapper.MedWithdrawalLockMapper;
import com.evops.aqua.med.service.MedBatchService;
import com.evops.aqua.med.service.MedDrugService;
import com.evops.aqua.med.service.MedRequisitionService;
import com.evops.aqua.service.PondService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 用药领用"休药期上下文隔离"修复验收（aqua-17）。
 * 主线：同一药品被两个池塘领用——
 * 1. 跨池并发提交：各明细只按本行冻结快照算休药结束日，两池锁定互不串用，多药叠加仍取最晚；
 * 2. 同池并发提交：池塘互斥串行重算，不丢更新、不互相覆盖；
 * 3. 跨池并发审核同一批号：条件更新兜底，恰一单一审核成功，库存不超卖、失败独立留证；
 * 4. 同一批数据撤回再重提：新编号（-R2/-R3）、批次关联版本（linkVersion）与各次时间逐版可辨，
 *    旧撤回单与历史审核结论原样保留，绝不静默重算。
 */
@SpringBootTest
class MedWithdrawalContextIsolationTest {

    @Autowired private PondService pondService;
    @Autowired private MedDrugService drugService;
    @Autowired private MedBatchService batchService;
    @Autowired private MedRequisitionService requisitionService;
    @Autowired private MedRequisitionMapper requisitionMapper;
    @Autowired private MedRequisitionItemMapper itemMapper;
    @Autowired private MedBatchMapper medBatchMapper;
    @Autowired private MedWithdrawalLockMapper lockMapper;
    @Autowired private MedChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();

    private Long p1, p2, pc, pd, pr, pj;
    private Long dX, dY, dZ, dW;
    private Long bX1, bY1, bZ1, bW1;

    private static final LocalDate USE = LocalDate.of(2026, 10, 10);

    @Test
    void crossPondConcurrentSubmitAndWithdrawResubmit() {
        checkpoint("CP0", "建档：6个池（100㎡×1m=100m³）、4个药品（休药35/10/25/7天）、4个批号", () -> {
            p1 = pond("CTX-P1", "并发一号池");
            p2 = pond("CTX-P2", "并发二号池");
            pc = pond("CTX-PC", "竞争审核池C");
            pd = pond("CTX-PD", "竞争审核池D");
            pr = pond("CTX-PR", "重提池");
            pj = pond("CTX-PJ", "驳回池");

            dX = drug("D-X", "恩诺沙星X", "1", "35", "drug-x");
            dY = drug("D-Y", "氟苯尼考Y", "1", "10", "drug-y");
            dZ = drug("D-Z", "聚维酮碘Z", "1", "25", "drug-z");
            dW = drug("D-W", "高锰酸钾W", "0.5", "7", "drug-w");

            bX1 = batch("BX-01", dX, "1000", "in-x1");
            bY1 = batch("BY-01", dY, "1000", "in-y1");
            bZ1 = batch("BZ-01", dZ, "1000", "in-z1");
            bW1 = batch("BW-01", dW, "80", "in-w1");
        });

        checkpoint("CP1", "跨池并发建草稿：一号池X(35天)；二号池Y(10天)+Z(25天)多药叠加", () -> {
            Long ra = createDraft(p1, "ctx-req-a", "ctx-a", item(dX, bX1, null));
            Long rb = createDraft(p2, "ctx-req-b", "ctx-b",
                    item(dY, bY1, null), item(dZ, bZ1, null));

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> errA = new AtomicReference<>();
            AtomicReference<Throwable> errB = new AtomicReference<>();
            Thread ta = new Thread(() -> raceSubmit(barrier, ra, "ctx-submit-a", "申请人甲", errA), "submit-p1");
            Thread tb = new Thread(() -> raceSubmit(barrier, rb, "ctx-submit-b", "申请人乙", errB), "submit-p2");
            ta.start();
            tb.start();
            ta.join(15000);
            tb.join(15000);
            check(errA.get() == null, "一号池并发提交异常：" + errA.get());
            check(errB.get() == null, "二号池并发提交异常：" + errB.get());

            check(MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(ra).getStatus())
                    && MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(rb).getStatus()),
                    "两张跨池领用单并发提交后均应为待审核");

            // 各明细休药结束日只取本行冻结快照，绝不串用另一池/另一药
            MedRequisitionItem aItem = onlyItem(ra);
            check("2026-11-14".equals(aItem.getWithdrawalEndDate().toString())
                            && Integer.valueOf(35).equals(aItem.getWithdrawalDays()),
                    "一号池X药结束日应为10-10+35=11-14，实际=" + aItem.getWithdrawalEndDate());
            List<MedRequisitionItem> bItems = items(rb);
            check("2026-10-20".equals(bItems.get(0).getWithdrawalEndDate().toString())
                            && "2026-11-04".equals(bItems.get(1).getWithdrawalEndDate().toString()),
                    "二号池Y/Z结束日应为10-20/11-04，实际="
                            + bItems.get(0).getWithdrawalEndDate() + "/" + bItems.get(1).getWithdrawalEndDate());

            // 修复前的故障表象：较早批次(10/20)的休药期被套到另一池，一号池锁定被提前
            MedWithdrawalLock lock1 = lockOf(p1);
            MedWithdrawalLock lock2 = lockOf(p2);
            check(lock1 != null && "2026-11-14".equals(lock1.getLockedUntil().toString())
                            && lock1.getDetail().contains(requisitionMapper.selectById(ra).getReqNo()),
                    "一号池必须锁定到本池X药的11-14，实际=" + (lock1 == null ? "无锁" : lock1.getLockedUntil()));
            check(lock2 != null && "2026-11-04".equals(lock2.getLockedUntil().toString())
                            && lock2.getDetail().contains(requisitionMapper.selectById(rb).getReqNo()),
                    "二号池多药叠加必须取最晚11-04且不被一号池带偏，实际="
                            + (lock2 == null ? "无锁" : lock2.getLockedUntil()));
        });

        checkpoint("CP2", "同池并发提交：两张草稿同刻提交，互斥串行重算不丢更新", () -> {
            Long ra2 = createDraft(p1, "ctx-req-a2", "ctx-a2", item(dY, bY1, null));
            Long ra3 = createDraft(p1, "ctx-req-a3", "ctx-a3", item(dZ, bZ1, null));
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> err = new AtomicReference<>();
            Thread ta = new Thread(() -> raceSubmit(barrier, ra2, "ctx-submit-a2", "申请人甲", err), "submit-a2");
            Thread tb = new Thread(() -> raceSubmit(barrier, ra3, "ctx-submit-a3", "申请人甲", err), "submit-a3");
            ta.start();
            tb.start();
            ta.join(15000);
            tb.join(15000);
            check(err.get() == null, "同池并发提交异常：" + err.get());
            check(MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(ra2).getStatus())
                            && MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(ra3).getStatus()),
                    "同池两张单并发提交都必须成功落库（串行不丢单）");
            check(onlyItem(ra2).getWithdrawalEndDate().toString().equals("2026-10-20")
                            && onlyItem(ra3).getWithdrawalEndDate().toString().equals("2026-11-04"),
                    "同池两单结束日应各自正确（10-20/11-04）");
            MedWithdrawalLock lock1 = lockOf(p1);
            check(lock1 != null && "2026-11-14".equals(lock1.getLockedUntil().toString()),
                    "同池三单在途锁定必须重算为全局最晚11-14，实际="
                            + (lock1 == null ? "无锁" : lock1.getLockedUntil()));
        });

        checkpoint("CP3", "跨池并发审核同一批号：库存80、两单各需50，恰一单成功且不超卖、失败留证", () -> {
            Long rc = createDraft(pc, "ctx-req-c", "ctx-c", item(dW, bW1, null));
            Long rd = createDraft(pd, "ctx-req-d", "ctx-d", item(dW, bW1, null));
            submit(rc, "ctx-submit-c");
            submit(rd, "ctx-submit-d");
            check(new BigDecimal("50.00").compareTo(onlyItem(rc).getEstimatedQty()) == 0
                            && new BigDecimal("50.00").compareTo(onlyItem(rd).getEstimatedQty()) == 0,
                    "每池水体100m³×0.5剂量应各需50");

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> errC = new AtomicReference<>();
            AtomicReference<Throwable> errD = new AtomicReference<>();
            Thread tc = new Thread(() -> raceApprove(barrier, rc, "ctx-apv-c", errC), "approve-c");
            Thread td = new Thread(() -> raceApprove(barrier, rd, "ctx-apv-d", errD), "approve-d");
            tc.start();
            td.start();
            tc.join(15000);
            td.join(15000);

            boolean cOk = MedRequisition.STATUS_APPROVED.equals(requisitionMapper.selectById(rc).getStatus());
            boolean dOk = MedRequisition.STATUS_APPROVED.equals(requisitionMapper.selectById(rd).getStatus());
            check(cOk ^ dOk, "跨池并发审核同批号必须恰有一单成功，实际c=" + cOk + ",d=" + dOk);
            Long loserId = cOk ? rd : rc;
            Throwable loserErr = cOk ? errD.get() : errC.get();
            check(MedRequisition.STATUS_PENDING.equals(requisitionMapper.selectById(loserId).getStatus())
                            && loserErr != null && loserErr.getMessage().contains("库存不足"),
                    "失败单必须停留待审核并报库存不足，实际=" + (loserErr == null ? null : loserErr.getMessage()));
            MedBatch shared = medBatchMapper.selectById(bW1);
            check(new BigDecimal("50").compareTo(shared.getQuantityConsumed()) == 0
                            && new BigDecimal("30").compareTo(shared.availableQuantity()) == 0,
                    "并发审核后批号只扣一次50余30，绝不超卖，实际已扣=" + shared.getQuantityConsumed());
            long failed = evidenceMapper.selectCount(new QueryWrapper<MedChangeEvidence>()
                    .eq("action", MedChangeEvidence.ACTION_REQ_APPROVE_FAILED)
                    .in("entity_id", Arrays.asList(rc, rd)));
            long approved = evidenceMapper.selectCount(new QueryWrapper<MedChangeEvidence>()
                    .eq("action", MedChangeEvidence.ACTION_REQ_APPROVE)
                    .in("entity_id", Arrays.asList(rc, rd)));
            check(failed == 1 && approved == 1,
                    "并发审核应恰有1条成功留证+1条失败独立留证，实际失败=" + failed + "，成功=" + approved);
        });

        checkpoint("CP4", "撤回后重提：新编号-R2、批次关联版本v2、重提时间可辨，旧单与旧审核上下文原样保留", () -> {
            Long v1 = createDraft(pr, "ctx-req-r", "ctx-r", item(dY, bY1, null));
            submit(v1, "ctx-submit-r");
            String v1No = requisitionMapper.selectById(v1).getReqNo();
            MedWithdrawalLock beforeLock = lockOf(pr);
            check(beforeLock != null && "2026-10-20".equals(beforeLock.getLockedUntil().toString()),
                    "重提前重提池锁定应为Y药10-20");
            withdraw(v1, "ctx-wd-r");

            MedRequisition old = requisitionMapper.selectById(v1);
            check(MedRequisition.STATUS_WITHDRAWN.equals(old.getStatus())
                            && old.getWithdrawTime() != null
                            && Integer.valueOf(1).equals(old.getRevNo()),
                    "撤回后旧单必须保留为已撤回且v1、带撤回时间");
            check(lockOf(pr) == null, "撤回后重提池锁定应释放");
            MedRequisitionItem v1Item = onlyItem(v1);
            check(Integer.valueOf(1).equals(v1Item.getLinkVersion())
                            && "BY-01".equals(v1Item.getBatchNoSnapshot())
                            && bY1.equals(v1Item.getMedBatchId()),
                    "旧单明细关联版本v1/批号快照必须保留");

            // 重提：改用 Z 批号（批次关联变化）并覆盖剂量2，用水100 → 估算200
            RequisitionResubmitRequest rs = new RequisitionResubmitRequest();
            rs.setOperator("申请人甲");
            rs.setRequestId("ctx-resubmit-r2");
            rs.setItems(Arrays.asList(item(dZ, bZ1, "2")));
            MedRequisition v2 = requisitionService.resubmit(v1, rs);
            Long v2Id = v2.getId();

            check(MedRequisition.STATUS_PENDING.equals(v2.getStatus()), "重提单应直接进入待审核");
            check((v1No + "-R2").equals(v2.getReqNo()),
                    "重提编号必须可辨：" + v1No + "-R2，实际=" + v2.getReqNo());
            check(Integer.valueOf(2).equals(v2.getRevNo())
                            && v1No.equals(v2.getOriginReqNo())
                            && v1.equals(v2.getResubmitOfId())
                            && v2.getResubmitTime() != null
                            && v2.getResubmitTime().isAfter(old.getWithdrawTime()),
                    "重提单版本号=2、版本链起点=" + v1No + "、指向上一版、重提时间晚于撤回时间");
            MedRequisitionItem v2Item = onlyItem(v2Id);
            check(Integer.valueOf(2).equals(v2Item.getLinkVersion())
                            && "BZ-01".equals(v2Item.getBatchNoSnapshot())
                            && bZ1.equals(v2Item.getMedBatchId()),
                    "重提明细批次关联版本必须为v2并冻结新批号BZ-01");
            check(new BigDecimal("200.00").compareTo(v2Item.getEstimatedQty()) == 0
                            && Integer.valueOf(25).equals(v2Item.getWithdrawalDays())
                            && "2026-11-04".equals(v2Item.getWithdrawalEndDate().toString()),
                    "重提按新版本快照重新冻结剂量(2×100=200)/休药期(Z=25→11-04)，实际qty="
                            + v2Item.getEstimatedQty());
            check(lockOf(pr) != null && "2026-11-04".equals(lockOf(pr).getLockedUntil().toString()),
                    "重提后重提池应按新版本锁定到11-04");

            // 旧单与旧明细、旧审核/撤回证据不得被静默重算
            MedRequisition oldAfter = requisitionMapper.selectById(v1);
            MedRequisitionItem oldItemAfter = onlyItem(v1);
            check(MedRequisition.STATUS_WITHDRAWN.equals(oldAfter.getStatus())
                            && Integer.valueOf(1).equals(oldAfter.getRevNo())
                            && bY1.equals(oldItemAfter.getMedBatchId())
                            && new BigDecimal("100.00").compareTo(oldItemAfter.getEstimatedQty()) == 0
                            && "2026-10-20".equals(oldItemAfter.getWithdrawalEndDate().toString()),
                    "历史撤回单及其剂量/休药期/批号关联不得被重提静默重算");
            List<String> v1Actions = new ArrayList<>();
            evidenceMapper.selectList(new QueryWrapper<MedChangeEvidence>()
                            .eq("entity_id", v1).orderByAsc("id"))
                    .forEach(e -> v1Actions.add(e.getAction()));
            check(v1Actions.equals(Arrays.asList("REQ_CREATE", "REQ_SUBMIT", "REQ_WITHDRAW")),
                    "旧单证据链必须原样为创建/提交/撤回，实际=" + v1Actions);
            check(evidenceMapper.selectCount(new QueryWrapper<MedChangeEvidence>()
                    .eq("entity_id", v2Id).eq("action", MedChangeEvidence.ACTION_REQ_RESUBMIT)) == 1L,
                    "重提必须独立留证 REQ_RESUBMIT");

            // 同 requestId 重放幂等：不产生第三张单
            MedRequisition replay = requisitionService.resubmit(v1, rs);
            check(replay.getId().equals(v2Id), "重提同requestId重放应返回同一新版本单");
            check(requisitionMapper.selectCount(new QueryWrapper<MedRequisition>()
                    .eq("resubmit_of_id", v1)) == 1L, "幂等重放不得产生第二张重提单");

            // 非撤回单（已驳回）禁止重提
            Long vj = createDraft(pj, "ctx-req-j", "ctx-j", item(dY, bY1, null));
            submit(vj, "ctx-submit-j");
            requisitionService.reject(vj, review("陈兽医", "证据不足驳回", "ctx-rej-j"));
            assertThrows(() -> requisitionService.resubmit(vj,
                    resubmitBody("ctx-resubmit-j-bad")), "仅已撤回单可重提");
        });

        checkpoint("CP5", "二次撤回再重提：-R3、关联版本v3、撤回/重提时间逐版可辨", () -> {
            MedRequisition v2 = requisitionMapper.selectOne(new QueryWrapper<MedRequisition>()
                    .eq("request_id", "ctx-resubmit-r2"));
            withdraw(v2.getId(), "ctx-wd-r2");
            MedRequisition v2After = requisitionMapper.selectById(v2.getId());
            check(v2After.getWithdrawTime() != null
                            && v2After.getWithdrawTime().isAfter(v2After.getResubmitTime()),
                    "v2撤回时间必须晚于其重提时间，逐版时间可辨");
            check(lockOf(pr) == null, "v2撤回后重提池锁定应再次释放");

            // 缺省沿用上一版明细（Z批号、剂量2）
            MedRequisition v3 = requisitionService.resubmit(v2.getId(),
                    resubmitBody("ctx-resubmit-r3"));
            String base = v2.getOriginReqNo();
            check((base + "-R3").equals(v3.getReqNo())
                            && Integer.valueOf(3).equals(v3.getRevNo())
                            && base.equals(v3.getOriginReqNo())
                            && v2.getId().equals(v3.getResubmitOfId()),
                    "第三版编号应为" + base + "-R3且版本链连续，实际=" + v3.getReqNo() + "/v" + v3.getRevNo());
            MedRequisitionItem v3Item = onlyItem(v3.getId());
            check(Integer.valueOf(3).equals(v3Item.getLinkVersion())
                            && "BZ-01".equals(v3Item.getBatchNoSnapshot())
                            && new BigDecimal("200.00").compareTo(v3Item.getEstimatedQty()) == 0,
                    "v3明细沿用上一版（BZ-01、剂量2），关联版本=3");
            check(v3.getResubmitTime().isAfter(v2After.getWithdrawTime()),
                    "v3重提时间必须晚于v2撤回时间");

            // 版本链三张单并存、终态各自正确
            List<MedRequisition> chain = requisitionMapper.selectList(
                    new QueryWrapper<MedRequisition>().eq("origin_req_no", base).orderByAsc("rev_no"));
            check(chain.size() == 3
                            && MedRequisition.STATUS_WITHDRAWN.equals(chain.get(0).getStatus())
                            && MedRequisition.STATUS_WITHDRAWN.equals(chain.get(1).getStatus())
                            && MedRequisition.STATUS_PENDING.equals(chain.get(2).getStatus()),
                    "版本链应三张单并存：v1/v2已撤回保留、v3待审核，实际=" + chain.size());
            check(lockOf(pr) != null && "2026-11-04".equals(lockOf(pr).getLockedUntil().toString()),
                    "v3在途时重提池应锁定到11-04");
        });

        report.add("验收结论：全部检查点通过——跨池并发提交休药期各按本明细快照不串批、同池并发互斥串行不丢更新、跨池并发审核同批号不超卖且失败留证、多药叠加取最晚、历史审核结果不静默重算、撤回重提编号/批次关联版本/时间逐版可辨");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private void raceSubmit(CyclicBarrier barrier, Long id, String requestId, String operator,
                            AtomicReference<Throwable> err) {
        try {
            barrier.await();
            requisitionService.submit(id, action(operator, requestId));
        } catch (Throwable t) {
            err.set(t);
        }
    }

    private void raceApprove(CyclicBarrier barrier, Long id, String requestId,
                             AtomicReference<Throwable> err) {
        try {
            barrier.await();
            requisitionService.approve(id, review("陈兽医", "并发审核同意", requestId));
        } catch (Throwable t) {
            err.set(t);
        }
    }

    private Long pond(String code, String name) {
        PondCreateRequest req = new PondCreateRequest();
        req.setPondCode(code);
        req.setPondName(name);
        req.setSeaArea("东山湾海区");
        req.setWaterAreaM2(new BigDecimal("100.00"));
        req.setWaterDepthM(new BigDecimal("1.00"));
        req.setSalinityPpt(new BigDecimal("28.5"));
        Pond pond = pondService.create(req);
        return pond.getId();
    }

    private Long drug(String code, String name, String dosage, String withdrawalDays, String requestId) {
        MedDrugRequest req = new MedDrugRequest();
        req.setDrugCode(code);
        req.setDrugName(name);
        req.setSpec("100g/袋");
        req.setUnit("g");
        req.setDosagePerM3(new BigDecimal(dosage));
        req.setWithdrawalDays(Integer.valueOf(withdrawalDays));
        req.setOperator("药政员");
        req.setRequestId(requestId);
        return drugService.register(req).getId();
    }

    private Long batch(String batchNo, Long drugId, String qty, String requestId) {
        MedBatchInRequest req = new MedBatchInRequest();
        req.setBatchNo(batchNo);
        req.setDrugId(drugId);
        req.setSupplier("闽东兽药经营部");
        req.setQuantityIn(new BigDecimal(qty));
        req.setProductionDate(LocalDate.of(2026, 6, 1));
        req.setExpiryDate(LocalDate.of(2027, 6, 1));
        req.setOperator("仓管员王库");
        req.setRequestId(requestId);
        return batchService.stockIn(req).getId();
    }

    private RequisitionItemRequest item(Long drugId, Long batchId, String dosage) {
        RequisitionItemRequest item = new RequisitionItemRequest();
        item.setDrugId(drugId);
        item.setMedBatchId(batchId);
        if (dosage != null) {
            item.setDosagePerM3(new BigDecimal(dosage));
        }
        return item;
    }

    private Long createDraft(Long pondId, String requestId, String applicant, RequisitionItemRequest... items) {
        RequisitionCreateRequest req = new RequisitionCreateRequest();
        req.setPondId(pondId);
        req.setDiseaseDesc("疑似细菌性感染，按方领用");
        req.setUseDate(USE);
        req.setApplicant(applicant);
        req.setRequestId(requestId);
        req.setItems(Arrays.asList(items));
        return requisitionService.create(req).getId();
    }

    private void submit(Long id, String requestId) {
        requisitionService.submit(id, action("申请人甲", requestId));
    }

    private void withdraw(Long id, String requestId) {
        requisitionService.withdraw(id, action("申请人甲", requestId));
    }

    private RequisitionActionRequest action(String operator, String requestId) {
        RequisitionActionRequest req = new RequisitionActionRequest();
        req.setOperator(operator);
        req.setRequestId(requestId);
        return req;
    }

    private RequisitionReviewRequest review(String by, String remark, String requestId) {
        RequisitionReviewRequest req = new RequisitionReviewRequest();
        req.setApprovedBy(by);
        req.setApproveRemark(remark);
        req.setRequestId(requestId);
        return req;
    }

    private RequisitionResubmitRequest resubmitBody(String requestId) {
        RequisitionResubmitRequest req = new RequisitionResubmitRequest();
        req.setOperator("申请人甲");
        req.setRequestId(requestId);
        return req;
    }

    private MedRequisitionItem onlyItem(Long requisitionId) {
        List<MedRequisitionItem> items = items(requisitionId);
        if (items.size() != 1) {
            throw new IllegalStateException("期望单行明细，实际" + items.size() + "行");
        }
        return items.get(0);
    }

    private List<MedRequisitionItem> items(Long requisitionId) {
        return itemMapper.selectList(new QueryWrapper<MedRequisitionItem>()
                .eq("requisition_id", requisitionId).orderByAsc("id"));
    }

    private MedWithdrawalLock lockOf(Long pondId) {
        return lockMapper.selectOne(new QueryWrapper<MedWithdrawalLock>()
                .eq("pond_id", pondId).last("limit 1"));
    }

    private void assertThrows(RunnableThrowing body, String expected) {
        try {
            body.run();
        } catch (IllegalArgumentException ex) {
            check(ex.getMessage() != null && ex.getMessage().contains(expected),
                    "异常信息应包含「" + expected + "」，实际=" + ex.getMessage());
            return;
        }
        throw new IllegalStateException("应抛出包含「" + expected + "」的异常，但未抛出");
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
        sb.append("\n========== 休药期上下文隔离修复验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("================================================\n");
        System.out.println(sb);
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }

    private interface RunnableThrowing {
        void run();
    }
}
