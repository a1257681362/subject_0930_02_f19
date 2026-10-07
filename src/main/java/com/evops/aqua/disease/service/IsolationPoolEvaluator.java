package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseIsolation;
import com.evops.aqua.disease.entity.PondGuard;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 隔离池调度评估器：推荐与正式调度共用的校验规则。
 * 逐条收集冲突原因（不快速失败），调度失败时一次性说明全部原因：
 * 池状态、隔离池角色、调入权限、消毒状态、容量、混养占用。
 */
@Service
public class IsolationPoolEvaluator {

    private final SeedlingBatchService seedlingBatchService;
    private final IsolationGuard isolationGuard;

    public IsolationPoolEvaluator(SeedlingBatchService seedlingBatchService,
                                  IsolationGuard isolationGuard) {
        this.seedlingBatchService = seedlingBatchService;
        this.isolationGuard = isolationGuard;
    }

    /**
     * 评估目标池能否承接调入，返回全部冲突原因（空列表=可调入）。
     */
    public List<String> evaluate(Pond target, PondGuard guard, int incomingQty, LocalDate dispatchDate) {
        List<String> reasons = new ArrayList<>();
        if (!Pond.STATUS_ACTIVE.equals(target.getStatus())) {
            reasons.add("目标池" + target.getPondCode() + "已停用");
        }
        if (guard == null) {
            reasons.add("目标池" + target.getPondCode() + "未登记隔离调度档案，不是可用隔离池");
        } else {
            if (!PondGuard.ROLE_ISOLATION.equals(guard.getPondRole())) {
                reasons.add("目标池" + target.getPondCode() + "档案角色为普通池，不是隔离池");
            }
            if (!Boolean.TRUE.equals(guard.getTransferInAllowed())) {
                reasons.add("目标池" + target.getPondCode() + "调入权限已关闭");
            }
            if (guard.getDisinfectedUntil() == null) {
                reasons.add("目标池" + target.getPondCode() + "未登记消毒记录");
            } else if (guard.getDisinfectedUntil().isBefore(dispatchDate)) {
                reasons.add("目标池" + target.getPondCode() + "消毒已过期：消毒有效期至"
                        + guard.getDisinfectedUntil() + "，调度日" + dispatchDate);
            }
            int currentStock = currentStock(target.getId());
            if (guard.getCapacity() == null) {
                reasons.add("目标池" + target.getPondCode() + "未登记容量，无法校验容量");
            } else if (currentStock + incomingQty > guard.getCapacity()) {
                reasons.add("目标池" + target.getPondCode() + "容量不足：在养" + currentStock
                        + "尾+调入" + incomingQty + "尾>容量" + guard.getCapacity() + "尾");
            }
        }
        DiseaseIsolation active = isolationGuard.activeIsolationInPond(target.getId());
        if (active != null) {
            reasons.add("目标池" + target.getPondCode() + "已有在隔离批次（隔离单"
                    + active.getIsolationNo() + "），禁止混养");
        }
        return reasons;
    }

    /** 池内当前在养数量（在养批次数量求和）。 */
    public int currentStock(Long pondId) {
        List<SeedlingBatch> batches = seedlingBatchService.list(new QueryWrapper<SeedlingBatch>()
                .eq("pond_id", pondId)
                .eq("status", SeedlingBatch.STATUS_STOCKED));
        int sum = 0;
        for (SeedlingBatch batch : batches) {
            sum += batch.getQuantity() == null ? 0 : batch.getQuantity();
        }
        return sum;
    }
}
