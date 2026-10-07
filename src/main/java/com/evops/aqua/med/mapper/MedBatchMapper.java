package com.evops.aqua.med.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.aqua.med.entity.MedBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface MedBatchMapper extends BaseMapper<MedBatch> {

    /**
     * 条件扣减：仅当可用量（入库-已消耗）充足时才落账，一条 SQL 完成读-判-写，
     * 杜绝两个池塘并发审核同批号时"先查可用、后各自扣减"造成的超卖。
     * 扣减后恰好用尽则同步置 EXHAUSTED。
     *
     * @return 受影响行数：1 扣减成功；0 库存不足（或批号不存在），调用方须整体回滚
     */
    @Update("UPDATE t_med_batch SET "
            + "quantity_consumed = quantity_consumed + #{quantity}, "
            + "status = CASE WHEN quantity_in <= quantity_consumed + #{quantity} "
            + "THEN 'EXHAUSTED' ELSE status END, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND quantity_in - quantity_consumed >= #{quantity}")
    int deductIfAvailable(@Param("id") Long id, @Param("quantity") BigDecimal quantity);
}
