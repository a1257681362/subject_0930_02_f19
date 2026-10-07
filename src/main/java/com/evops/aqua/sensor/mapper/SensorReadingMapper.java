package com.evops.aqua.sensor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.aqua.sensor.entity.SensorReading;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensorReadingMapper extends BaseMapper<SensorReading> {
}
