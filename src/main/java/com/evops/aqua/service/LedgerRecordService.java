package com.evops.aqua.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.dto.DailyRecordRequest;
import com.evops.aqua.dto.FeedRecordRequest;
import com.evops.aqua.dto.InspectionRecordRequest;
import com.evops.aqua.dto.MedicationRecordRequest;
import com.evops.aqua.entity.DailyRecord;
import com.evops.aqua.entity.FeedRecord;
import com.evops.aqua.entity.InspectionRecord;
import com.evops.aqua.entity.MedicationRecord;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.DailyRecordMapper;
import com.evops.aqua.mapper.FeedRecordMapper;
import com.evops.aqua.mapper.InspectionRecordMapper;
import com.evops.aqua.mapper.MedicationRecordMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 养殖过程台账：日常记录、饲料、用药、抽检，全部按池按批留痕。
 */
@Service
public class LedgerRecordService {

    private final SeedlingBatchService batchService;
    private final DailyRecordMapper dailyRecordMapper;
    private final FeedRecordMapper feedRecordMapper;
    private final MedicationRecordMapper medicationRecordMapper;
    private final InspectionRecordMapper inspectionRecordMapper;

    public LedgerRecordService(SeedlingBatchService batchService,
                               DailyRecordMapper dailyRecordMapper,
                               FeedRecordMapper feedRecordMapper,
                               MedicationRecordMapper medicationRecordMapper,
                               InspectionRecordMapper inspectionRecordMapper) {
        this.batchService = batchService;
        this.dailyRecordMapper = dailyRecordMapper;
        this.feedRecordMapper = feedRecordMapper;
        this.medicationRecordMapper = medicationRecordMapper;
        this.inspectionRecordMapper = inspectionRecordMapper;
    }

    public DailyRecord addDaily(DailyRecordRequest request) {
        requireBatchInPond(request.getBatchId(), request.getPondId());
        DailyRecord record = new DailyRecord();
        BeanUtils.copyProperties(request, record);
        dailyRecordMapper.insert(record);
        return record;
    }

    public FeedRecord addFeed(FeedRecordRequest request) {
        requireBatchInPond(request.getBatchId(), request.getPondId());
        FeedRecord record = new FeedRecord();
        BeanUtils.copyProperties(request, record);
        feedRecordMapper.insert(record);
        return record;
    }

    public MedicationRecord addMedication(MedicationRecordRequest request) {
        requireBatchInPond(request.getBatchId(), request.getPondId());
        MedicationRecord record = new MedicationRecord();
        BeanUtils.copyProperties(request, record);
        medicationRecordMapper.insert(record);
        return record;
    }

    public InspectionRecord addInspection(InspectionRecordRequest request) {
        requireBatchInPond(request.getBatchId(), request.getPondId());
        InspectionRecord record = new InspectionRecord();
        BeanUtils.copyProperties(request, record);
        inspectionRecordMapper.insert(record);
        return record;
    }

    public List<DailyRecord> listDaily(Long pondId, Long batchId) {
        return dailyRecordMapper.selectList(pondBatchWrapper(pondId, batchId));
    }

    public List<FeedRecord> listFeed(Long pondId, Long batchId) {
        return feedRecordMapper.selectList(pondBatchWrapper(pondId, batchId));
    }

    public List<MedicationRecord> listMedication(Long pondId, Long batchId) {
        return medicationRecordMapper.selectList(pondBatchWrapper(pondId, batchId));
    }

    public List<InspectionRecord> listInspection(Long pondId, Long batchId) {
        return inspectionRecordMapper.selectList(pondBatchWrapper(pondId, batchId));
    }

    /**
     * 按池按批留痕的前提：批次必须在养、且当前就在该池。
     */
    private SeedlingBatch requireBatchInPond(Long batchId, Long pondId) {
        SeedlingBatch batch = batchService.getOrThrow(batchId);
        batchService.requireStocked(batch, "登记台账");
        if (!batch.getPondId().equals(pondId)) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "当前不在池塘id=" + pondId + "，请核对池号或先登记换池");
        }
        return batch;
    }

    private <T> QueryWrapper<T> pondBatchWrapper(Long pondId, Long batchId) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByAsc("id");
        return wrapper;
    }
}
