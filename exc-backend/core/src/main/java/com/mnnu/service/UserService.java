package com.mnnu.service;
import com.mnnu.dto.UserDTO;
import com.mnnu.dto.UserTradeStatsDTO;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录（验证签名并返回 token）
     */
    String login(String address, String signature);


    BigDecimal getExthBalance(String address);

    boolean isRegistered(String address);

    /**
     * 获取用户信息
     */
    UserDTO getUserInfo(String address);


    /**
     * 计算可交易UT
     */
    Long calculateTradeableUt(String address);

    /**
     * 检查用户是否在黑名单
     */
    boolean isBlacklisted(String address);


    UserTradeStatsDTO getUserTradeStats(String address);


}

