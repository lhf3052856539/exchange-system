package com.mnnu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;


import com.mnnu.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 根据地址查询用户
     */
    @Select("SELECT * FROM t_user WHERE address = #{address}")
    UserEntity selectByAddress(@Param("address") String address);

    /**
     * 更新用户类型
     */
    @Update("UPDATE t_user SET user_type = #{userType}, update_time = NOW() " +
            "WHERE address = #{address}")
    int updateUserType(@Param("address") String address,
                       @Param("userType") Integer userType);

    /**
     * 减少新用户率先转账次数
     */
    @Update("UPDATE t_user SET new_user_trade_count = new_user_trade_count - 1, " +
            "update_time = NOW() WHERE address = #{address} AND new_user_trade_count > 0")
    int decreaseNewUserCount(@Param("address") String address);

    /**
     * 拉黑用户
     */
    @Update("UPDATE t_user SET is_blacklisted = 1, update_time = NOW() " +
            "WHERE address = #{address}")
    int blacklistUser(@Param("address") String address);
}
