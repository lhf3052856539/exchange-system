package com.mnnu.mapper;
/**
 * 通知Mapper
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mnnu.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    /**
     * 标记为已读
     */
    @Update("UPDATE t_notification SET is_read = 1, read_time = NOW() " +
            "WHERE id = #{id} AND address = #{address}")
    int markAsRead(@Param("id") Long id, @Param("address") String address);

    /**
     * 批量标记已读
     */
    @Update("UPDATE t_notification SET is_read = 1, read_time = NOW() " +
            "WHERE address = #{address} AND is_read = 0")
    int markAllAsRead(@Param("address") String address);
}
