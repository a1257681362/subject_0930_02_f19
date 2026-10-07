package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseFollowupTask;
import com.evops.aqua.disease.mapper.DiseaseFollowupTaskMapper;
import com.evops.aqua.maint.entity.MaintWorker;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.entity.WorkOrderWorker;
import com.evops.aqua.maint.mapper.MaintWorkerMapper;
import com.evops.aqua.maint.mapper.WorkOrderMapper;
import com.evops.aqua.maint.mapper.WorkOrderWorkerMapper;
import com.evops.aqua.qual.dto.BatchFreezeRequest;
import com.evops.aqua.qual.entity.FreezeBatch;
import com.evops.aqua.qual.entity.FreezeLine;
import com.evops.aqua.qual.entity.QualPerson;
import com.evops.aqua.qual.mapper.FreezeBatchMapper;
import com.evops.aqua.qual.mapper.FreezeLineMapper;
import com.evops.aqua.qual.mapper.QualPersonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量冻结：逐人冻结并列出影响清单——
 * 受影响工单（保养工单未关闭，经维修工号=人员编号关联）与未完成任务（疫病复查任务待处理）。
 * 批次 requestId 幂等：重放返回首次批次与影响快照，不重复冻结、不重复写行。
 */
@Service
public class FreezeService {

    private final QualPersonMapper personMapper;
    private final FreezeBatchMapper batchMapper;
    private final FreezeLineMapper lineMapper;
    private final MaintWorkerMapper workerMapper;
    private final WorkOrderWorkerMapper orderWorkerMapper;
    private final WorkOrderMapper orderMapper;
    private final DiseaseFollowupTaskMapper followupTaskMapper;
    private final QualEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public FreezeService(QualPersonMapper personMapper, FreezeBatchMapper batchMapper,
                         FreezeLineMapper lineMapper, MaintWorkerMapper workerMapper,
                         WorkOrderWorkerMapper orderWorkerMapper, WorkOrderMapper orderMapper,
                         DiseaseFollowupTaskMapper followupTaskMapper,
                         QualEvidenceService evidenceService, ObjectMapper objectMapper) {
        this.personMapper = personMapper;
        this.batchMapper = batchMapper;
        this.lineMapper = lineMapper;
        this.workerMapper = workerMapper;
        this.orderWorkerMapper = orderWorkerMapper;
        this.orderMapper = orderMapper;
        this.followupTaskMapper = followupTaskMapper;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> freezeBatch(BatchFreezeRequest request) {
        FreezeBatch existed = batchMapper.selectOne(
                new QueryWrapper<FreezeBatch>().eq("request_id", request.getRequestId()));
        if (existed != null) {
            return toResponse(existed, true);
        }
        FreezeBatch batch = new FreezeBatch();
        batch.setRequestId(request.getRequestId());
        batch.setOperator(request.getOperator());
        batch.setReason(request.getReason());
        List<String> codes = distinctCodes(request.getPersonCodes());
        batch.setPersonCount(codes.size());
        batchMapper.insert(batch);

        for (String code : codes) {
            QualPerson person = personMapper.selectOne(
                    new QueryWrapper<QualPerson>().eq("person_code", code));
            FreezeLine line = new FreezeLine();
            line.setBatchId(batch.getId());
            line.setPersonCode(code);
            if (person == null) {
                line.setResult(FreezeLine.RESULT_NOT_FOUND);
                line.setAffectedOrdersJson("[]");
                line.setUnfinishedTasksJson("[]");
            } else {
                line.setPersonId(person.getId());
                line.setPersonName(person.getPersonName());
                List<Map<String, Object>> orders = affectedOrders(code);
                List<Map<String, Object>> tasks = unfinishedTasks(code);
                line.setAffectedOrdersJson(toJson(orders));
                line.setUnfinishedTasksJson(toJson(tasks));
                if (QualPerson.STATUS_FROZEN.equals(person.getStatus())) {
                    line.setResult(FreezeLine.RESULT_ALREADY_FROZEN);
                } else {
                    String before = "status=" + person.getStatus();
                    person.setStatus(QualPerson.STATUS_FROZEN);
                    personMapper.updateById(person);
                    line.setResult(FreezeLine.RESULT_FROZEN);
                    evidenceService.record("PERSON", person.getId(), "PERSON_FREEZE",
                            request.getRequestId(), request.getOperator(), before, "status=FROZEN",
                            "批量冻结人员[" + code + "]，受影响工单 " + orders.size()
                                    + " 张、未完成任务 " + tasks.size() + " 项，原因：" + request.getReason());
                }
            }
            lineMapper.insert(line);
        }
        evidenceService.record("FREEZE_BATCH", batch.getId(), "BATCH_FREEZE",
                request.getRequestId(), request.getOperator(), null, batch,
                "批量冻结 " + codes.size() + " 人，原因：" + request.getReason());
        return toResponse(batch, false);
    }

    /** 受影响工单：人员编号=维修工号，关联工单未关闭（未 CLOSED）即受影响。 */
    private List<Map<String, Object>> affectedOrders(String personCode) {
        List<Map<String, Object>> orders = new ArrayList<>();
        MaintWorker worker = workerMapper.selectOne(
                new QueryWrapper<MaintWorker>().eq("worker_code", personCode));
        if (worker == null) {
            return orders;
        }
        List<WorkOrderWorker> links = orderWorkerMapper.selectList(
                new QueryWrapper<WorkOrderWorker>().eq("worker_id", worker.getId()));
        for (WorkOrderWorker link : links) {
            WorkOrder order = orderMapper.selectById(link.getWorkOrderId());
            if (order == null || "CLOSED".equals(order.getStatus())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", order.getOrderNo());
            row.put("status", order.getStatus());
            row.put("deviceId", order.getDeviceId());
            row.put("schedStart", order.getSchedStart() == null ? null : order.getSchedStart().toString());
            orders.add(row);
        }
        return orders;
    }

    /** 未完成任务：疫病复查任务操作者为该人员且待处理。 */
    private List<Map<String, Object>> unfinishedTasks(String personCode) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<DiseaseFollowupTask> pending = followupTaskMapper.selectList(
                new QueryWrapper<DiseaseFollowupTask>()
                        .eq("operator", personCode)
                        .eq("status", "PENDING")
                        .orderByAsc("id"));
        for (DiseaseFollowupTask task : pending) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskNo", task.getTaskNo());
            row.put("taskDate", task.getTaskDate() == null ? null : task.getTaskDate().toString());
            row.put("observationId", task.getObservationId());
            tasks.add(row);
        }
        return tasks;
    }

    private Map<String, Object> toResponse(FreezeBatch batch, boolean replayed) {
        List<FreezeLine> lines = lineMapper.selectList(new QueryWrapper<FreezeLine>()
                .eq("batch_id", batch.getId()).orderByAsc("id"));
        List<Map<String, Object>> lineViews = new ArrayList<>();
        for (FreezeLine line : lines) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("personCode", line.getPersonCode());
            view.put("personName", line.getPersonName());
            view.put("result", line.getResult());
            view.put("affectedOrders", readJson(line.getAffectedOrdersJson()));
            view.put("unfinishedTasks", readJson(line.getUnfinishedTasksJson()));
            lineViews.add(view);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("batchId", batch.getId());
        response.put("requestId", batch.getRequestId());
        response.put("replayed", replayed);
        response.put("personCount", batch.getPersonCount());
        response.put("lines", lineViews);
        return response;
    }

    private List<String> distinctCodes(List<String> personCodes) {
        List<String> codes = new ArrayList<>();
        if (personCodes == null) {
            return codes;
        }
        for (String code : personCodes) {
            if (code != null && !code.trim().isEmpty() && !codes.contains(code.trim())) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("冻结人员名单不能为空");
        }
        return codes;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null ? "[]" : json);
        } catch (Exception ex) {
            return objectMapper.createArrayNode();
        }
    }
}
