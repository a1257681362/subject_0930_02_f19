package com.evops.aqua.med.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 池塘休药锁定互斥键：每池一行。
 * 业务事务内先 MERGE 建行（取行锁至提交），再 SELECT ... FOR UPDATE 显式持锁，
 * 把同一池塘的休药锁定重算串行化；不同池塘键不同，跨池提交互不阻塞。
 */
@Mapper
public interface MedPondMutexMapper {

    @Update("MERGE INTO t_med_pond_mutex (pond_id) KEY (pond_id) VALUES (#{pondId})")
    void ensureKey(@Param("pondId") Long pondId);

    @Select("SELECT pond_id FROM t_med_pond_mutex WHERE pond_id = #{pondId} FOR UPDATE")
    Long lockPond(@Param("pondId") Long pondId);
}
