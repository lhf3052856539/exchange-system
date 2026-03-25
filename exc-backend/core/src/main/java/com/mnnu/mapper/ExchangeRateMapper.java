package com.mnnu.mapper;
/**
 * 汇率记录Mapper
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mnnu.entity.ExchangeRateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRateEntity> {

    /**
     * 查询最新汇率
     */
    @Select("SELECT * FROM t_exchange_rate WHERE pair = #{pair} " +
            "ORDER BY update_time DESC LIMIT 1")
    ExchangeRateEntity selectLatest(@Param("pair") String pair);
}
