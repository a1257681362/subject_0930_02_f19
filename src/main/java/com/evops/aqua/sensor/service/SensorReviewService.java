package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.ReviewResolveRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewTask;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常读数待复核队列：超范围读数入队，暂不计入日报。
 * 复核确认（CONFIRMED）补入未审核日报；复核剔除（EXCLUDED）永不计入。
 * 日报已审核的，复核结论留痕但不回改日报。
 */
@Service
public class SensorReviewService {

    private final SensorReviewTaskMapper reviewTaskMapper;
    private final SensorReadingMapper readingMapper;
    private final SensorDailyReportService dailyReportService;
    private final SensorEvidenceService evidenceService;

    public SensorReviewService(SensorReviewTaskMapper reviewTaskMapper,
                               SensorReadingMapper readingMapper,
                               SensorDailyReportService dailyReportService,
                               SensorEvidenceService evidenceService) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.readingMapper = readingMapper;
        this.dailyReportService = dailyReportService;
        this.evidenceService = evidenceService;
    }

    /**
     * 复核异常读数。幂等：相同结论重复复核直接返回；不同结论拒绝改判。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorReviewTask resolve(Long taskId, ReviewResolveRequest request) {
        SensorReviewTask task = getOrThrow(taskId);
        String decision = request.getDecision().trim().toUpperCase();
        if (!SensorReviewTask.STATUS_CONFIRMED.equals(decision)
                && !SensorReviewTask.STATUS_EXCLUDED.equals(decision)) {
            throw new IllegalArgumentException("复核结论只能是 CONFIRMED 或 EXCLUDED：" + request.getDecision());
        }
        if (!SensorReviewTask.STATUS_PENDING.equals(task.getStatus())) {
            if (task.getStatus().equals(decision)) {
                return task; // 同结论重复复核：幂等返回
            }
            throw new IllegalArgumentException("复核任务已办结（" + task.getStatus() + "），不能改判为 " + decision);
        }

        SensorReading reading = readingMapper.selectById(task.getReadingId());
        if (reading == null) {
            throw new IllegalStateException("复核任务关联的读数不存在，readingId=" + task.getReadingId());
        }
        SensorReviewTask beforeTask = copyOf(task);
        task.setStatus(decision);
        task.setReviewedByName(request.getReviewedByName());
        task.setReviewedTime(LocalDateTime.now());
        task.setReviewRemark(request.getReviewRemark());
        reviewTaskMapper.updateById(task);

        String readingStatus = SensorReviewTask.STATUS_CONFIRMED.equals(decision)
                ? SensorReading.STATUS_CONFIRMED : SensorReading.STATUS_EXCLUDED;
        String beforeReadingStatus = reading.getStatus();
        reading.setStatus(readingStatus);
        readingMapper.updateById(reading);

        boolean confirmed = SensorReviewTask.STATUS_CONFIRMED.equals(decision);
        evidenceService.record(SensorChangeEvidence.ENTITY_REVIEW_TASK, task.getId(),
                confirmed ? SensorChangeEvidence.ACTION_CONFIRM : SensorChangeEvidence.ACTION_EXCLUDE,
                request.getRequestId(), beforeTask, task,
                "异常读数复核" + (confirmed ? "确认有效" : "剔除") + "，复核人：" + request.getReviewedByName()
                        + "，意见：" + request.getReviewRemark());
        evidenceService.record(SensorChangeEvidence.ENTITY_READING, reading.getId(),
                SensorChangeEvidence.ACTION_UPDATE, request.getRequestId(),
                "status=" + beforeReadingStatus, "status=" + readingStatus,
                "复核结论同步读数状态：" + beforeReadingStatus + " → " + readingStatus);

        // 确认有效才影响日报；且仅当日报未审核时重算补入，已审核日报冻结不回改
        if (confirmed) {
            if (dailyReportService.isReviewed(reading.getPondId(),
                    reading.getDeviceTime().toLocalDate(), reading.getMetricType())) {
                evidenceService.record(SensorChangeEvidence.ENTITY_READING, reading.getId(),
                        SensorChangeEvidence.ACTION_UPDATE, request.getRequestId(), null, null,
                        "日报已审核锁定，复核确认结论留痕但不回改日报");
            } else {
                dailyReportService.recalc(reading.getPondId(), reading.getDeviceTime().toLocalDate(),
                        reading.getMetricType(), reading.getRuleVersionId(), request.getRequestId());
            }
        }
        return task;
    }

    public SensorReviewTask getOrThrow(Long id) {
        SensorReviewTask task = reviewTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("复核任务不存在，id=" + id);
        }
        return task;
    }

    public List<SensorReviewTask> listQueue(String status, Long pondId) {
        QueryWrapper<SensorReviewTask> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.orderByAsc("id");
        return reviewTaskMapper.selectList(wrapper);
    }

    private SensorReviewTask copyOf(SensorReviewTask source) {
        SensorReviewTask copy = new SensorReviewTask();
        copy.setId(source.getId());
        copy.setReadingId(source.getReadingId());
        copy.setPondId(source.getPondId());
        copy.setMetricType(source.getMetricType());
        copy.setDeviceTime(source.getDeviceTime());
        copy.setReadingValue(source.getReadingValue());
        copy.setStatus(source.getStatus());
        copy.setReviewedByName(source.getReviewedByName());
        copy.setReviewedTime(source.getReviewedTime());
        copy.setReviewRemark(source.getReviewRemark());
        return copy;
    }
}
