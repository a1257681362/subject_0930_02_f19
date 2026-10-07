package com.evops.aqua.release.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.aqua.release.entity.ReleaseSigner;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReleaseSignerMapper extends BaseMapper<ReleaseSigner> {
}
