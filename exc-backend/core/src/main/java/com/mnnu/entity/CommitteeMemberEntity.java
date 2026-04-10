package com.mnnu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_committee_member")
public class CommitteeMemberEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String address;
    private String name;

    @TableField("is_active")
    private Boolean isActive;

    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
