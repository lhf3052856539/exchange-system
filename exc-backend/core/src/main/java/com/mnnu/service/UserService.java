package com.mnnu.service;
import com.mnnu.dto.UserDTO;
import com.mnnu.dto.UserTradeStatsDTO;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录（验证签名并返回 token）
     */
    String login(String address, String signature);

    /**
     * 用户注册
     */
    UserDTO register(String address);

    boolean isRegistered(String address);

    /**
     * 获取用户信息
     */
    UserDTO getUserInfo(String address);

    /**
     * 更新用户类型
     */
    UserDTO updateUserType(String address);

    @Async("userTaskExecutor")
    void asyncUpdateUserType(String address);

    /**
     * 计算可交易UT
     */
    Long calculateTradeableUt(String address);

    /**
     * 检查用户是否在黑名单
     */
    boolean isBlacklisted(String address);

    /**
     * 拉黑用户
     */
    void blacklistUser(String address, String reason);


    UserTradeStatsDTO getUserTradeStats(String address);

    /**
     * 增加用户交易次数
     */
    void incrementTradeCount(String address);

    /**
     * 从链上同步用户 EXTH 余额到数据库
     */
    void updateExthBalanceOnChain(String address);

}

