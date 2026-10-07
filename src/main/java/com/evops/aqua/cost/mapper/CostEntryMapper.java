package com.evops.aqua.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.aqua.cost.entity.CostEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CostEntryMapper extends BaseMapper<CostEntry> {
}
