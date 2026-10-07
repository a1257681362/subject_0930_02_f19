package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.SpareCatalogRequest;
import com.evops.aqua.maint.dto.SparePartStockRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.PartReplacement;
import com.evops.aqua.maint.entity.SpareCatalog;
import com.evops.aqua.maint.entity.SparePart;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.entity.WorkOrderPart;
import com.evops.aqua.maint.mapper.PartReplacementMapper;
import com.evops.aqua.maint.mapper.SpareCatalogMapper;
import com.evops.aqua.maint.mapper.SparePartMapper;
import com.evops.aqua.maint.mapper.WorkOrderPartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备件：目录 + 一物一序列号实例。
 * 工单建立即按需求行锁定在库实例（RESERVED）；换件时新件装机（INSTALLED）、
 * 旧件转报废留存（SCRAPPED）且旧序列号永久保留；完工未用的锁定件释放回在库。
 */
@Service
public class SpareService {

    private final SpareCatalogMapper catalogMapper;
    private final SparePartMapper partMapper;
    private final PartReplacementMapper replacementMapper;
    private final WorkOrderPartMapper orderPartMapper;
    private final MaintEvidenceService evidenceService;

    public SpareService(SpareCatalogMapper catalogMapper,
                        SparePartMapper partMapper,
                        PartReplacementMapper replacementMapper,
                        WorkOrderPartMapper orderPartMapper,
                        MaintEvidenceService evidenceService) {
        this.catalogMapper = catalogMapper;
        this.partMapper = partMapper;
        this.replacementMapper = replacementMapper;
        this.orderPartMapper = orderPartMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SpareCatalog registerCatalog(SpareCatalogRequest request) {
        Long duplicated = catalogMapper.selectCount(
                new QueryWrapper<SpareCatalog>().eq("part_code", request.getPartCode()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("备件编码已存在：" + request.getPartCode());
        }
        SpareCatalog catalog = new SpareCatalog();
        catalog.setPartCode(request.getPartCode());
        catalog.setPartName(request.getPartName());
        catalog.setSpec(request.getSpec());
        catalog.setSafetyStock(request.getSafetyStock() == null ? 0 : request.getSafetyStock());
        catalogMapper.insert(catalog);
        return catalog;
    }

    /** 实物入库：一物一序列号；同序列号重复入库返回首次实例（自然键幂等）。 */
    @Transactional(rollbackFor = Exception.class)
    public SparePart stockIn(SparePartStockRequest request) {
        getCatalogOrThrow(request.getCatalogId());
        SparePart existing = partMapper.selectOne(
                new QueryWrapper<SparePart>().eq("serial_no", request.getSerialNo()));
        if (existing != null) {
            return existing;
        }
        SparePart part = new SparePart();
        part.setCatalogId(request.getCatalogId());
        part.setSerialNo(request.getSerialNo());
        part.setStatus(SparePart.STATUS_IN_STOCK);
        partMapper.insert(part);
        return part;
    }

    public SpareCatalog getCatalogOrThrow(Long catalogId) {
        SpareCatalog catalog = catalogMapper.selectById(catalogId);
        if (catalog == null) {
            throw new IllegalArgumentException("备件品类不存在，id=" + catalogId);
        }
        return catalog;
    }

    public List<SparePart> listParts(Long catalogId, String status) {
        QueryWrapper<SparePart> wrapper = new QueryWrapper<>();
        wrapper.eq(catalogId != null, "catalog_id", catalogId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return partMapper.selectList(wrapper);
    }

    /**
     * 为工单按需求行锁定在库备件实例（数量不足整单回滚，不残留半锁定——由调用方同事务保证）。
     * 返回 catalogId -> 锁定实例。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, List<SparePart>> reserveForOrder(WorkOrder order, List<WorkOrderPart> lines) {
        Map<Long, List<SparePart>> reserved = new LinkedHashMap<>();
        for (WorkOrderPart line : lines) {
            SpareCatalog catalog = getCatalogOrThrow(line.getCatalogId());
            List<SparePart> available = partMapper.selectList(new QueryWrapper<SparePart>()
                    .eq("catalog_id", line.getCatalogId())
                    .eq("status", SparePart.STATUS_IN_STOCK)
                    .orderByAsc("id")
                    .last("LIMIT " + line.getQuantity()));
            if (available.size() < line.getQuantity()) {
                throw new IllegalArgumentException("备件[" + catalog.getPartName() + "]在库可用量不足：需要"
                        + line.getQuantity() + "，可用" + available.size()
                        + "；工单未建立，不产生任何锁定");
            }
            List<SparePart> held = new ArrayList<>();
            for (SparePart part : available) {
                part.setStatus(SparePart.STATUS_RESERVED);
                part.setReservedWorkOrderId(order.getId());
                partMapper.updateById(part);
                held.add(part);
            }
            line.setStatus(WorkOrderPart.STATUS_RESERVED);
            orderPartMapper.updateById(line);
            reserved.put(line.getCatalogId(), held);
        }
        return reserved;
    }

    /**
     * 备件替换（同事务）：
     * - 新件序列号必须是本工单锁定的实例且与旧件同品类；
     * - 旧件序列号必须是当前装在该设备上的实例；
     * - 旧件转 SCRAPPED 保留（原装机信息与 removed_work_order_id 不清除），新件转 INSTALLED；
     * - 追加换件记录，requestId 幂等；该品类需求行换满数量置 REPLACED。
     */
    @Transactional(rollbackFor = Exception.class)
    public PartReplacement replace(WorkOrder order, MaintDevice device, Long catalogId,
                                   PartReplaceRequest request) {
        PartReplacement existing = replacementMapper.selectOne(
                new QueryWrapper<PartReplacement>().eq("request_id", request.getRequestId()));
        if (existing != null) {
            return existing;
        }
        getCatalogOrThrow(catalogId);
        SparePart newPart = partMapper.selectOne(
                new QueryWrapper<SparePart>().eq("serial_no", request.getNewSerialNo()));
        if (newPart == null) {
            throw new IllegalArgumentException("新件序列号未入库：" + request.getNewSerialNo());
        }
        if (!newPart.getCatalogId().equals(catalogId)) {
            throw new IllegalArgumentException("新件[" + newPart.getSerialNo()
                    + "]不属于本工单所需备件品类，不能装机");
        }
        if (!SparePart.STATUS_RESERVED.equals(newPart.getStatus())
                || !order.getId().equals(newPart.getReservedWorkOrderId())) {
            throw new IllegalArgumentException("新件[" + newPart.getSerialNo()
                    + "]不是本工单已锁定的备件，不能装机");
        }
        SparePart oldPart = partMapper.selectOne(
                new QueryWrapper<SparePart>().eq("serial_no", request.getRemovedSerialNo()));
        if (oldPart == null) {
            throw new IllegalArgumentException("旧件序列号在备件台账中不存在：" + request.getRemovedSerialNo()
                    + "——替换必须留存台账在册旧件的序列号");
        }
        if (!oldPart.getCatalogId().equals(catalogId)) {
            throw new IllegalArgumentException("旧件[" + oldPart.getSerialNo()
                    + "]与新件不属于同一备件品类，不能互相替换");
        }
        if (!SparePart.STATUS_INSTALLED.equals(oldPart.getStatus())
                || !device.getId().equals(oldPart.getInstalledDeviceId())) {
            throw new IllegalArgumentException("旧件[" + oldPart.getSerialNo()
                    + "]当前未装在设备[" + device.getDeviceCode() + "]上，不能作为旧件替换");
        }

        LocalDateTime now = LocalDateTime.now();
        // 旧件：转报废留存，序列号与原装机信息保留
        SparePart beforeOld = copyOf(oldPart);
        oldPart.setStatus(SparePart.STATUS_SCRAPPED);
        oldPart.setRemovedWorkOrderId(order.getId());
        oldPart.setRemovedAt(now);
        partMapper.updateById(oldPart);
        // 新件：装机
        SparePart beforeNew = copyOf(newPart);
        newPart.setStatus(SparePart.STATUS_INSTALLED);
        newPart.setReservedWorkOrderId(null);
        newPart.setInstalledDeviceId(device.getId());
        newPart.setInstalledWorkOrderId(order.getId());
        newPart.setInstalledAt(now);
        partMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SparePart>()
                .eq("id", newPart.getId())
                .set("status", SparePart.STATUS_INSTALLED)
                .set("reserved_work_order_id", null)
                .set("installed_device_id", device.getId())
                .set("installed_work_order_id", order.getId())
                .set("installed_at", now));

        PartReplacement replacement = new PartReplacement();
        replacement.setWorkOrderId(order.getId());
        replacement.setCatalogId(catalogId);
        replacement.setNewSerialNo(request.getNewSerialNo());
        replacement.setRemovedSerialNo(request.getRemovedSerialNo());
        replacement.setReplacedBy(request.getReplacedBy());
        replacement.setReplacedAt(now);
        replacement.setRequestId(request.getRequestId());
        replacement.setRemark(request.getRemark());
        replacementMapper.insert(replacement);

        // 需求行换满数量置 REPLACED
        WorkOrderPart line = orderPartMapper.selectOne(new QueryWrapper<WorkOrderPart>()
                .eq("work_order_id", order.getId()).eq("catalog_id", catalogId));
        if (line != null) {
            Long replacedCount = replacementMapper.selectCount(new QueryWrapper<PartReplacement>()
                    .eq("work_order_id", order.getId()).eq("catalog_id", catalogId));
            if (replacedCount != null && replacedCount >= line.getQuantity()) {
                line.setStatus(WorkOrderPart.STATUS_REPLACED);
                orderPartMapper.updateById(line);
            }
        }

        evidenceService.record(MaintChangeEvidence.ENTITY_REPLACEMENT, replacement.getId(),
                MaintChangeEvidence.ACTION_REPLACE_PART, request.getRequestId(), request.getReplacedBy(),
                java.util.Arrays.asList(beforeOld, beforeNew),
                java.util.Arrays.asList(oldPart, newPart),
                "设备[" + device.getDeviceCode() + "]换件：旧件序列号[" + request.getRemovedSerialNo()
                        + "]报废留存，新件序列号[" + request.getNewSerialNo() + "]装机，换件人="
                        + request.getReplacedBy());
        return replacement;
    }

    /**
     * 完工释放：工单已锁定但未装机的备件释放回在库；未换满的需求行置 CANCELLED。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseUnused(WorkOrder order) {
        List<SparePart> reserved = partMapper.selectList(new QueryWrapper<SparePart>()
                .eq("reserved_work_order_id", order.getId())
                .eq("status", SparePart.STATUS_RESERVED));
        for (SparePart part : reserved) {
            // 显式置空保留工单：MyBatis-Plus updateById 默认忽略 null 字段，须用 UpdateWrapper.set
            part.setStatus(SparePart.STATUS_IN_STOCK);
            part.setReservedWorkOrderId(null);
            partMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SparePart>()
                    .eq("id", part.getId())
                    .set("status", SparePart.STATUS_IN_STOCK)
                    .set("reserved_work_order_id", null));
        }
        List<WorkOrderPart> lines = orderPartMapper.selectList(
                new QueryWrapper<WorkOrderPart>().eq("work_order_id", order.getId()));
        for (WorkOrderPart line : lines) {
            if (WorkOrderPart.STATUS_RESERVED.equals(line.getStatus())
                    || WorkOrderPart.STATUS_PLANNED.equals(line.getStatus())) {
                line.setStatus(WorkOrderPart.STATUS_CANCELLED);
                orderPartMapper.updateById(line);
            }
        }
    }

    /**
     * 设备初始装机：在库实物装到设备上，形成资产序列号基线（无工单，installed_work_order_id 留空）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SparePart installInitial(MaintDevice device, String serialNo, String operator, String remark) {
        SparePart part = partMapper.selectOne(new QueryWrapper<SparePart>().eq("serial_no", serialNo));
        if (part == null) {
            throw new IllegalArgumentException("备件序列号未入库：" + serialNo);
        }
        if (!SparePart.STATUS_IN_STOCK.equals(part.getStatus())) {
            throw new IllegalArgumentException("备件[" + serialNo + "]当前状态=" + part.getStatus()
                    + "，只有在库备件可以初始装机");
        }
        SparePart before = copyOf(part);
        part.setStatus(SparePart.STATUS_INSTALLED);
        part.setInstalledDeviceId(device.getId());
        part.setInstalledAt(LocalDateTime.now());
        partMapper.updateById(part);
        evidenceService.record(MaintChangeEvidence.ENTITY_DEVICE, device.getId(),
                MaintChangeEvidence.ACTION_INSTALL_INITIAL, null, operator,
                before, part, "设备[" + device.getDeviceCode() + "]初始装机备件序列号[" + serialNo
                        + "]，登记人=" + operator + (remark == null ? "" : "，备注=" + remark));
        return part;
    }

    public List<PartReplacement> listReplacements(Long workOrderId) {
        return replacementMapper.selectList(new QueryWrapper<PartReplacement>()
                .eq("work_order_id", workOrderId).orderByAsc("id"));
    }

    public PartReplacement findReplacementByRequest(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        return replacementMapper.selectOne(
                new QueryWrapper<PartReplacement>().eq("request_id", requestId));
    }

    private SparePart copyOf(SparePart source) {
        SparePart copy = new SparePart();
        copy.setId(source.getId());
        copy.setCatalogId(source.getCatalogId());
        copy.setSerialNo(source.getSerialNo());
        copy.setStatus(source.getStatus());
        copy.setReservedWorkOrderId(source.getReservedWorkOrderId());
        copy.setInstalledDeviceId(source.getInstalledDeviceId());
        copy.setInstalledWorkOrderId(source.getInstalledWorkOrderId());
        copy.setInstalledAt(source.getInstalledAt());
        copy.setRemovedWorkOrderId(source.getRemovedWorkOrderId());
        copy.setRemovedAt(source.getRemovedAt());
        return copy;
    }
}
