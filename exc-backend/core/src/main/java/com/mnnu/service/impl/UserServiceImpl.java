package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.UserDTO;
import com.mnnu.dto.UserTradeStatsDTO;
import com.mnnu.entity.TradeRecordEntity;
import com.mnnu.entity.UserEntity;
import com.mnnu.exception.BusinessException;
import com.mnnu.mapper.TradeMapper;
import com.mnnu.mapper.UserMapper;
import com.mnnu.service.*;
import com.mnnu.utils.AmountUtil;
import com.mnnu.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RateService rateService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private BlockchainService blockchainService;
    @Autowired
    private TradeMapper tradeRecordMapper;
    @Autowired
    private JwtUtil jwtUtil;

    // 手动编写的构造函数
    @Autowired
    public UserServiceImpl(UserMapper userMapper,
                           RateService rateService,
                           RedissonClient redissonClient,
                           @Lazy BlockchainService blockchainService,
                           TradeMapper tradeRecordMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.rateService = rateService;
        this.redissonClient = redissonClient;
        this.blockchainService = blockchainService;
        this.tradeRecordMapper = tradeRecordMapper;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户登录（连接钱包）
     * 逻辑：查库 -> 无记录则调用合约注册 -> 返回 Token (数据由监听器异步同步)
     */
    @Override
    public String login(String address, String signature) {
        if (!verifySignature(address, signature)) {
            throw new BusinessException("Invalid signature");
        }

        UserEntity user = userMapper.selectByAddress(address);

        if (user == null) {
            log.warn("User not in DB, return NOT_REGISTERED: {}", address);
            throw new BusinessException(400, "NOT_REGISTERED");
        }

        return jwtUtil.generateToken(address);
    }

    /**
     * 验证签名
     */
    private boolean verifySignature(String address, String signature) {
        //实现真实的 Web3j 签名校验逻辑
        return true;
    }


    /**
     * 获取 EXTH 余额
     */
    @Override
    public BigDecimal getExthBalance(String address) {
        UserEntity user = userMapper.selectByAddress(address);
        return user != null && user.getExthBalance() != null ? user.getExthBalance() : BigDecimal.ZERO;
    }


    /**
     * 检查用户是否已注册
     */
    @Override
    public boolean isRegistered(String address) {
        return userMapper.selectByAddress(address) != null;
    }

    /**
     * 获取用户信息（纯链下查询）
     */
    @Override
    public UserDTO getUserInfo(String address) {
        UserEntity user = userMapper.selectByAddress(address);
        if (user == null) {
            return null;
        }

        UserDTO dto = convertToDTO(user);
        // 实时计算可交易 UT
        dto.setTradeableUt(calculateTradeableUt(address));
        return dto;
    }

    /**
     * 计算用户可交易额度 (UT)
     */
    @Override
    public Long calculateTradeableUt(String address) {
        // 从数据库获取最新同步的余额
        BigDecimal exthBalance = getExthBalance(address);

        // 利用工具类计算基础额度
        BigDecimal tradeable = AmountUtil.calculateTradeableUt(exthBalance);

        // 限制在 1-70 UT 之间
        long result = tradeable.longValue();
        if (result < SystemConstants.TradeConstants.MIN_TRADE_UT) {
            result = SystemConstants.TradeConstants.MIN_TRADE_UT;
        }
        if (result > SystemConstants.TradeConstants.MAX_TRADE_UT) {
            result = SystemConstants.TradeConstants.MAX_TRADE_UT;
        }

        return result;
    }



    /**
     * 是否黑名单用户
     */
    @Override
    public boolean isBlacklisted(String address) {
        UserEntity user = userMapper.selectByAddress(address);
        return user != null && user.getIsBlacklisted();
    }

    /**
     * 获取用户交易统计信息
     */
    @Override
    public UserTradeStatsDTO getUserTradeStats(String address) {
        UserTradeStatsDTO stats = new UserTradeStatsDTO();
        stats.setAddress(address);

        LambdaQueryWrapper<TradeRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(TradeRecordEntity::getPartyA, address)
                .or().eq(TradeRecordEntity::getPartyB, address));

        List<TradeRecordEntity> userTrades = tradeRecordMapper.selectList(wrapper);

        if (userTrades == null || userTrades.isEmpty()) {
            return initEmptyStats(stats);
        }

        int completedTrades = 0;
        int disputedTrades = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalReward = BigDecimal.ZERO;

        for (TradeRecordEntity trade : userTrades) {
            if (trade.getStatus() == SystemConstants.TradeStatus.COMPLETED) {
                completedTrades++;
                if (trade.getAmount() != null) totalVolume = totalVolume.add(trade.getAmount());
                if (trade.getExthReward() != null) totalReward = totalReward.add(trade.getExthReward());
            }
            if (trade.getDisputeStatus() != null && trade.getDisputeStatus() > 0) {
                disputedTrades++;
            }
        }

        stats.setTotalTrades(userTrades.size());
        stats.setCompletedTrades(completedTrades);
        stats.setDisputedTrades(disputedTrades);
        stats.setTotalVolume(totalVolume);
        stats.setTotalReward(totalReward);
        stats.setSuccessRate(userTrades.size() > 0 ? (completedTrades * 100 / userTrades.size()) : 0);

        return stats;
    }

    private UserTradeStatsDTO initEmptyStats(UserTradeStatsDTO stats) {
        stats.setTotalTrades(0);
        stats.setCompletedTrades(0);
        stats.setDisputedTrades(0);
        stats.setTotalVolume(BigDecimal.ZERO);
        stats.setTotalReward(BigDecimal.ZERO);
        stats.setSuccessRate(0);
        return stats;
    }

    /**
     * 转换为DTO
     */
    private UserDTO convertToDTO(UserEntity entity) {
        if (entity == null) return null;

        UserDTO dto = new UserDTO();
        dto.setAddress(entity.getAddress());
        dto.setUserType(entity.getUserType());
        dto.setUserTypeDesc(getUserTypeDesc(entity.getUserType()));
        dto.setNewUserTradeCount(entity.getNewUserTradeCount());
        dto.setExthBalance(entity.getExthBalance());
        dto.setIsBlacklisted(entity.getIsBlacklisted());
        dto.setRegisterTime(entity.getRegisterTime());
        dto.setLastActiveTime(entity.getLastActiveTime());

        return dto;
    }

    /**
     * 获取用户类型描述
     */
    private String getUserTypeDesc(int type) {
        switch (type) {
            case 0: return "新用户";
            case 1: return "普通用户";
            case 2: return "种子用户";
            default: return "未知";
        }
    }
}