package com.evops.aqua.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.dto.PondCreateRequest;
import com.evops.aqua.dto.PondDisableRequest;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.event.PondDisabledEvent;
import com.evops.aqua.mapper.PondMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 养殖池档案。
 */
@Service
public class PondService {

    private final PondMapper pondMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PondService(PondMapper pondMapper, ApplicationEventPublisher eventPublisher) {
        this.pondMapper = pondMapper;
        this.eventPublisher = eventPublisher;
    }

    public Pond create(PondCreateRequest request) {
        Long exists = pondMapper.selectCount(
                new QueryWrapper<Pond>().eq("pond_code", request.getPondCode()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("养殖池编号已存在：" + request.getPondCode());
        }
        Pond pond = new Pond();
        BeanUtils.copyProperties(request, pond);
        pond.setStatus(Pond.STATUS_ACTIVE);
        pondMapper.insert(pond);
        return pond;
    }

    public Pond getOrThrow(Long id) {
        Pond pond = pondMapper.selectById(id);
        if (pond == null) {
            throw new IllegalArgumentException("养殖池不存在，id=" + id);
        }
        return pond;
    }

    public List<Pond> list() {
        return pondMapper.selectList(new QueryWrapper<Pond>().orderByAsc("id"));
    }

    public void requireActive(Pond pond) {
        if (!Pond.STATUS_ACTIVE.equals(pond.getStatus())) {
            throw new IllegalArgumentException("养殖池已停用，不能投苗或换入：" + pond.getPondCode());
        }
    }

    /**
     * 将养殖管理对象（池塘）置为无效。
     * 不删除任何历史数据；发布停用事件，成本报表/页面/导出随后同口径不再统计该池。
     */
    @Transactional(rollbackFor = Exception.class)
    public Pond disable(Long id, PondDisableRequest request) {
        Pond pond = getOrThrow(id);
        pond.setStatus(Pond.STATUS_DISABLED);
        pond.setDisabledReason(request.getReason());
        pond.setDisabledTime(LocalDateTime.now());
        pondMapper.updateById(pond);
        eventPublisher.publishEvent(new PondDisabledEvent(pond.getId(), request.getReason(),
                request.getOperator()));
        return pond;
    }
}
