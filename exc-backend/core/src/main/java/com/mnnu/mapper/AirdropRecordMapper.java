package com.mnnu.mapper;
/**
 * 空投记录Mapper
 */


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mnnu.entity.AirdropRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AirdropRecordMapper extends BaseMapper<AirdropRecordEntity> {

    /**
     * 查询地址是否已领取
     */
    @Select("SELECT COUNT(*) FROM t_airdrop_record WHERE address = #{address} AND is_claimed = 1")
    int countClaimed(@Param("address") String address);

    /**
     * 查询地址领取记录
     */
    @Select("SELECT * FROM t_airdrop_record WHERE address = #{address} " +
            "ORDER BY create_time DESC LIMIT 1")
    AirdropRecordEntity selectLastClaim(@Param("address") String address);
}
