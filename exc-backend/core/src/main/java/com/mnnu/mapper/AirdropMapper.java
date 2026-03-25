package com.mnnu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mnnu.entity.AirdropEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 空投数据访问接口
 */
@Mapper
public interface AirdropMapper extends BaseMapper<AirdropEntity> {

    /**
     * 根据地址查询空投记录
     */
    @Select("SELECT * FROM airdrop WHERE address = #{address}")
    AirdropEntity selectByAddress(@Param("address") String address);

    /**
     * 查询最新的空投配置
     */
    @Select("SELECT * FROM airdrop ORDER BY create_time DESC LIMIT 1")
    AirdropEntity selectLatest();
}
