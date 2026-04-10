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
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 */
@Slf4j
@Service
// 我们删除了 @RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RateService rateService;
    private final RedissonClient redissonClient;
    private final BlockchainService blockchainService;
    private final TradeMapper tradeRecordMapper;

    private final JwtUtil jwtUtil;

    // 这是我们手动编写的构造函数
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
     * 用户登录（验证签名并返回 token）
     */
    @Override
    public String login(String address, String signature) {
        if (!verifySignature(address, signature)) {
            throw new BusinessException("Invalid signature");
        }

        UserEntity user = userMapper.selectByAddress(address);

        // ✅ 如果本地没有，立即去链上查一次（解决前端刚注册、监听器还没同步的时间差问题）
        if (user == null) {
            boolean onChainRegistered = blockchainService.isUserRegisteredOnChain(address);
            if (!onChainRegistered) {
                throw new BusinessException("Not registered on chain");
            }

            // 链上有，本地没有 -> 立即手动同步一次，不让用户等监听器
            register(address);
            user = userMapper.selectByAddress(address);
        }

        return jwtUtil.generateToken(address);
    }

    /**
     * 验证签名
     */
    private boolean verifySignature(String address, String signature) {
        try {
            log.debug("Signature verification placeholder for: {}", address);
            return true;
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }


    /**
     * 获取 EXTH 余额（从链上读取，缓存到 Redis）
     * ✅ 改进：不再依赖 MySQL 的 exthBalance 字段
     */
    private BigDecimal getExthBalance(String address) {
        try {
            // 1. 优先从 Redis 缓存获取
            String balanceKey = "user:balance:" + address;
            String cached = (String) redissonClient.getBucket(balanceKey).get();
            if (cached != null) {
                log.debug("Balance cache hit for {}: {}", address, cached);
                return new BigDecimal(cached);
            }

            // 2. 从链上读取
            log.info("Fetching EXTH balance from chain for: {}", address);
            BigDecimal balance = blockchainService.getBalance(address, "EXTH");

            // 3. 写入 Redis（5 分钟缓存）
            redissonClient.getBucket(balanceKey).set(balance.toString(), 5, TimeUnit.MINUTES);

            return balance;
        } catch (Exception e) {
            log.error("Get EXTH balance failed for {}: {}", address, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
    /**
     * 注册用户
     * ✅ 改进：此方法现在仅作为“占位符”或“手动补偿”，主要注册逻辑应由前端调合约+后端监听完成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO register(String address) {
        // 1. 检查是否已注册
        UserEntity existingUser = userMapper.selectByAddress(address);
        if (existingUser != null) {
            return convertToDTO(existingUser); // 已存在则直接返回
        }

        // 2. ✅ 创建数据库记录（初始状态，等待监听器或异步任务更新链上真实状态）
        UserEntity newUser = new UserEntity();
        newUser.setAddress(address);
        newUser.setUserType(SystemConstants.UserType.NEW); // 默认为 NEW
        newUser.setNewUserTradeCount(3);
        newUser.setExthBalance(BigDecimal.ZERO);
        newUser.setIsBlacklisted(false);
        LocalDateTime now = LocalDateTime.now();
        newUser.setRegisterTime(now);
        newUser.setLastActiveTime(now);
        newUser.setCreateTime(now);
        newUser.setUpdateTime(now);

        userMapper.insert(newUser);
        log.info("User record created in database: {}", address);

        // 3. 异步同步链上真实状态（余额、类型等）
        asyncUpdateUserType(address);

        return convertToDTO(newUser);
    }

    /**
     * 检查用户是否已注册
     */
    @Override
    public boolean isRegistered(String address) {
        UserEntity user = userMapper.selectByAddress(address);
        return user != null;
    }

    /**
     * 获取用户信息
     * 改进：用户不存在时返回 null，让调用者决定如何处理
     */
    @Override
    public UserDTO getUserInfo(String address) {

        // 从数据库查询
        UserEntity user = userMapper.selectByAddress(address);
        if (user == null) {
            // 用户不存在时返回 null
            log.warn("User not found: {}", address);
            return null;
        }

        // 构建 DTO
        UserDTO dto = convertToDTO(user);
        dto.setTradeableUt(calculateTradeableUt(address));

        return dto;
    }

    /**
     * 更新用户类型（同步版本，用于定时任务）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO updateUserType(String address) {
        log.info("Updating user type for address: {}", address);

        UserEntity user = userMapper.selectByAddress(address);
        if (user == null) {
            log.warn("User not found: {}", address);
            throw new BusinessException("User not found");
        }

        // 获取最新 EXTH 余额（从链上）
        BigDecimal exthBalance = getExthBalance(address);
        log.info("Retrieved EXTH balance: {}", exthBalance);

        // 只更新余额和用户类型
        user.setExthBalance(exthBalance);

        int newType = determineUserType(exthBalance, user.getUserType());
        log.info("Determined new user type: {}", newType);

        if (newType != user.getUserType()) {
            user.setUserType(newType);
            userMapper.updateById(user);
            log.info("User type updated in database: {} -> {}", address, newType);
        }

        user.setLastActiveTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("User last active time updated");

        // 更新缓存
        UserDTO dto = convertToDTO(user);
        dto.setTradeableUt(calculateTradeableUt(address));
        log.info("User info cached");

        return dto;
    }

    /**
     * 异步更新用户类型（不阻塞主流程）
     * 用于登录后、注册后等场景
     */
    @Override
    @Async("userTaskExecutor")
    public void asyncUpdateUserType(String address) {
        try {
            log.info("Async updating user type for: {}", address);
            updateUserType(address);
        } catch (Exception e) {
            log.warn("Async update user type failed for {}: {}", address, e.getMessage());
        }
    }


    /**
     * 确定用户类型
     */
    @Override
    public Long calculateTradeableUt(String address) {
        // 获取EXTH余额（从链上）
        BigDecimal exthBalance = getExthBalance(address);

        // 计算可交易UT
        BigDecimal tradeable = AmountUtil.calculateTradeableUt(exthBalance);

        // 限制在1-70UT之间
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

        try {
            // 查询用户的所有交易记录
            LambdaQueryWrapper<TradeRecordEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.and(w -> w.eq(TradeRecordEntity::getPartyA, address)
                    .or().eq(TradeRecordEntity::getPartyB, address));

            List<TradeRecordEntity> userTrades = tradeRecordMapper.selectList(wrapper);

            if (userTrades == null || userTrades.isEmpty()) {
                // 没有交易记录，返回默认值
                stats.setTotalTrades(0);
                stats.setCompletedTrades(0);
                stats.setDisputedTrades(0);
                stats.setTotalVolume(BigDecimal.ZERO);
                stats.setTotalReward(BigDecimal.ZERO);
                stats.setSuccessRate(0);
                return stats;
            }

            // 统计数据
            int totalTrades = userTrades.size();
            int completedTrades = 0;
            int disputedTrades = 0;
            BigDecimal totalVolume = BigDecimal.ZERO;
            BigDecimal totalReward = BigDecimal.ZERO;

            for (TradeRecordEntity trade : userTrades) {
                // 统计已完成交易
                if (trade.getStatus() == SystemConstants.TradeStatus.COMPLETED) {
                    completedTrades++;
                    // 累加交易量
                    if (trade.getAmount() != null) {
                        totalVolume = totalVolume.add(trade.getAmount());
                    }
                    // 累加奖励
                    if (trade.getExthReward() != null) {
                        totalReward = totalReward.add(trade.getExthReward());
                    }
                }

                // 统计争议交易
                if (trade.getIsDisputed() != null && trade.getIsDisputed()) {
                    disputedTrades++;
                }
            }

            // 计算成功率
            int successRate = totalTrades > 0 ? (completedTrades * 100 / totalTrades) : 0;

            // 设置统计结果
            stats.setTotalTrades(totalTrades);
            stats.setCompletedTrades(completedTrades);
            stats.setDisputedTrades(disputedTrades);
            stats.setTotalVolume(totalVolume);
            stats.setTotalReward(totalReward);
            stats.setSuccessRate(successRate);

            log.debug("Trade stats calculated for {}: total={}, completed={}, volume={}",
                    address, totalTrades, completedTrades, totalVolume);

        } catch (Exception e) {
            log.error("Failed to calculate trade stats for {}: {}", address, e.getMessage());
            // 出错时返回默认值
            stats.setTotalTrades(0);
            stats.setCompletedTrades(0);
            stats.setDisputedTrades(0);
            stats.setTotalVolume(BigDecimal.ZERO);
            stats.setTotalReward(BigDecimal.ZERO);
            stats.setSuccessRate(0);
        }

        return stats;
    }

    /**
     * 增加用户交易次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementTradeCount(String address) {
        UserEntity user = userMapper.selectByAddress(address);
        if (user == null) {
            log.warn("User not found for trade count increment: {}", address);
            return;
        }

        // 增加交易次数（这里使用 newUserTradeCount 字段）
        if (user.getUserType() == SystemConstants.UserType.NEW && user.getNewUserTradeCount() > 0) {
            user.setNewUserTradeCount(user.getNewUserTradeCount() - 1);

            // 如果完成规定次数，升级为普通用户
            if (user.getNewUserTradeCount() == 0) {
                user.setUserType(SystemConstants.UserType.NORMAL);
                log.info("User upgraded to NORMAL after completing trades: {}", address);
            }
        }

        userMapper.updateById(user);


        log.debug("Trade count incremented for user: {}", address);
    }


    /**
     * 从链上获取最新 EXTH 余额并同步到数据库
     * 数据流向：链上合约 → Redis 缓存 + MySQL 数据库
     * 从链上获取最新 EXTH 余额并同步到 Redis 缓存
     * ✅ 改进：不再更新 MySQL，只更新 Redis 缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExthBalanceOnChain(String address) {
        // 从链上查询余额
        BigInteger balance = blockchainService.getBalance(address,"EXTH").toBigInteger();

        // 更新数据库
        UserEntity user = userMapper.selectByAddress(address);
        if (user != null) {
            user.setExthBalance(new BigDecimal(balance));
            userMapper.updateById(user);

            // 清除缓存
            redissonClient.getBucket("user:info:" + address).delete();
        }
    }



    /**
     * 确定用户类型
     */
    private int determineUserType(BigDecimal exthBalance, int currentType) {
        // 种子用户条件: EXTH >= 900
        long seedThreshold = SystemConstants.TradeConstants.SEED_USER_EXTH_AMOUNT;

        if (exthBalance.compareTo(BigDecimal.valueOf(seedThreshold)) >= 0) {
            return SystemConstants.UserType.SEED;
        } else {
            // 新用户只能通过注册成为，不能通过余额变化成为新用户
            return currentType == SystemConstants.UserType.NEW ?
                    SystemConstants.UserType.NEW : SystemConstants.UserType.NORMAL;
        }
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