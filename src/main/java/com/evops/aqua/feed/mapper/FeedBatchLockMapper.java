package com.evops.aqua.feed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.aqua.feed.entity.FeedBatchLock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedBatchLockMapper extends BaseMapper<FeedBatchLock> {
}
