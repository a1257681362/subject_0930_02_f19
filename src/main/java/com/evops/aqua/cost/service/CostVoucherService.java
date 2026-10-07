package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.VoucherCreateRequest;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostVoucherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务凭证登记薄：人工补录成本与差异调整必须引用 ACTIVE 凭证。
 */
@Service
public class CostVoucherService {

    private final CostVoucherMapper voucherMapper;
    private final CostEvidenceService evidenceService;

    public CostVoucherService(CostVoucherMapper voucherMapper, CostEvidenceService evidenceService) {
        this.voucherMapper = voucherMapper;
        this.evidenceService = evidenceService;
    }

    /** 登记凭证：requestId 与凭证号均幂等，重复请求返回首次凭证。 */
    @Transactional(rollbackFor = Exception.class)
    public CostVoucher create(VoucherCreateRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("凭证金额必须大于0");
        }
        CostVoucher exists = voucherMapper.selectOne(
                new QueryWrapper<CostVoucher>().eq("request_id", request.getRequestId()));
        if (exists != null) {
            return exists;
        }
        CostVoucher sameNo = voucherMapper.selectOne(
                new QueryWrapper<CostVoucher>().eq("voucher_no", request.getVoucherNo()));
        if (sameNo != null) {
            throw new IllegalArgumentException("财务凭证号已存在：" + request.getVoucherNo());
        }
        CostVoucher voucher = new CostVoucher();
        voucher.setVoucherNo(request.getVoucherNo());
        voucher.setVoucherType(request.getVoucherType());
        voucher.setVoucherDate(request.getVoucherDate());
        voucher.setAmount(request.getAmount());
        voucher.setSummary(request.getSummary());
        voucher.setStatus(CostVoucher.STATUS_ACTIVE);
        voucher.setSource("MANUAL");
        voucher.setRequestId(request.getRequestId());
        voucherMapper.insert(voucher);
        evidenceService.record(com.evops.aqua.cost.entity.CostChangeEvidence.ENTITY_VOUCHER,
                voucher.getId(), com.evops.aqua.cost.entity.CostChangeEvidence.ACTION_CREATE,
                request.getRequestId(), null, null, voucher,
                "登记财务凭证：" + voucher.getVoucherNo() + "，金额=" + voucher.getAmount());
        return voucher;
    }

    /** 按凭证号取有效凭证：不存在或已作废一律拒绝，人工补录无凭证可引。 */
    public CostVoucher requireActive(String voucherNo) {
        CostVoucher voucher = voucherMapper.selectOne(
                new QueryWrapper<CostVoucher>().eq("voucher_no", voucherNo));
        if (voucher == null) {
            throw new IllegalArgumentException("财务凭证不存在：" + voucherNo + "，人工补录必须先登记并引用凭证");
        }
        if (!CostVoucher.STATUS_ACTIVE.equals(voucher.getStatus())) {
            throw new IllegalArgumentException("财务凭证已作废，不能引用：" + voucherNo);
        }
        return voucher;
    }

    public List<CostVoucher> list() {
        return voucherMapper.selectList(new QueryWrapper<CostVoucher>().orderByAsc("id"));
    }
}
