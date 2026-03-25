package com.mnnu.service.impl;
/**
 * 交易服务实现
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.mnnu.exception.BusinessException;
import com.mnnu.exception.ValidationException;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.*;
import com.mnnu.entity.DisputeRecordEntity;
import com.mnnu.entity.TradeRecordEntity;
import com.mnnu.entity.UserEntity;
import com.mnnu.mapper.DisputeRecordMapper;
import com.mnnu.mapper.TradeRecordMapper;
import com.mnnu.mapper.UserMapper;
import com.mnnu.service.*;
import com.mnnu.utils.AmountUtil;
import com.mnnu.utils.Web3jUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final TradeRecordMapper tradeMapper;
    private final DisputeRecordMapper disputeMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final RateService rateService;
    private final RewardService rewardService;
    private final MatchingEngineService matchingEngine;
    private final NotificationServiceImpl notificationService;
    @Autowired
    @Lazy
    private BlockchainService blockchainService;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 请求匹配
     */
    @Override
    public TradeRequestDTO requestMatch(String address, TradeRequestParam param) {
        // 验证用户
        UserDTO user = userService.getUserInfo(address);
        if (user.getIsBlacklisted()) {
            throw new BusinessException("User is blacklisted");
        }

        // 验证交易额度
        if (param.getAmount() < SystemConstants.TradeConstants.MIN_TRADE_UT ||
                param.getAmount() > SystemConstants.TradeConstants.MAX_TRADE_UT) {
            throw new ValidationException("Invalid trade amount");
        }

        // 验证用户可交易额度
        Long tradeable = userService.calculateTradeableUt(address);
        if (param.getAmount() > tradeable) {
            throw new BusinessException("Amount exceeds tradeable limit");
        }

        // 验证币种
        if (!"RNB".equals(param.getFromCurrency()) && !"GBP".equals(param.getFromCurrency())) {
            throw new ValidationException("Invalid from currency");
        }
        if (!"RNB".equals(param.getToCurrency()) && !"GBP".equals(param.getToCurrency())) {
            throw new ValidationException("Invalid to currency");
        }
        if (param.getFromCurrency().equals(param.getToCurrency())) {
            throw new ValidationException("From and to currencies must be different");
        }

        // 添加到等待队列
        matchingEngine.addToWaitingQueue(address, param.getAmount(),
                param.getFromCurrency(), param.getToCurrency());

        // 触发匹配
        rabbitTemplate.convertAndSend(SystemConstants.MQQueue.TRADE_MATCH, "match");

        // 获取队列位置和统计信息
        WaitingQueueStatsDTO stats = matchingEngine.getWaitingQueueStats();
        Integer queuePosition = getQueuePosition(address, stats);

        TradeRequestDTO response = new TradeRequestDTO();
        response.setSuccess(true);
        response.setMessage("Added to matching queue");
        response.setQueuePosition(queuePosition != null ? queuePosition : 1);

        return response;
    }

    /**
     * 获取用户在队列中的位置
     */
    private Integer getQueuePosition(String address, WaitingQueueStatsDTO stats) {
        if (stats == null || stats.getWaitingList() == null) {
            return 1;
        }

        List<QueueItemDTO> waitingList = stats.getWaitingList();
        for (int i = 0; i < waitingList.size(); i++) {
            if (address.equals(waitingList.get(i).getAddress())) {
                return i + 1; // 位置从 1 开始
            }
        }

        return 1;
    }

    /**
     * 创建交易对
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeDTO createTradePair(TradeMatchDTO match) {
        // 生成交易 ID
        String tradeId = generateTradeId();

        // 获取汇率
        BigDecimal rate = match.getExchangeRate();
        if (rate == null) {
            rate = rateService.calculateExchangeAmount(BigDecimal.ONE,
                    match.getFromCurrency(), match.getToCurrency());
        }

        // 计算双方需转账金额（1 UT = 100 USD）
        // match.getAmount() 是 UT 数量，需要转换为 USD
        BigDecimal usdValue = BigDecimal.valueOf(match.getAmount()).multiply(new BigDecimal("100"));

        // A 方转出金额：根据 fromCurrency → toCurrency 的汇率计算
        BigDecimal amountA = usdValue.multiply(rate);  // A 转出 fromCurrency
        BigDecimal amountB = usdValue;                  // B 转出 toCurrency（等值 USD）

        // 检查奖励是否需要减半
        Long totalVolume = rewardService.getTotalUTVolume();
        if (rewardService.checkHalvingNeeded(totalVolume)) {
            log.info("Reward halving triggered at volume: {}", totalVolume);
        }

        // 创建交易记录
        TradeRecordEntity trade = new TradeRecordEntity();
        trade.setTradeId(tradeId);
        trade.setPartyA(match.getPartyA());
        trade.setPartyB(match.getPartyB());
        trade.setAmount(usdValue);
        trade.setAmountA(amountA);
        trade.setAmountB(amountB);
        trade.setFromCurrency(match.getFromCurrency());
        trade.setToCurrency(match.getToCurrency());
        trade.setExchangeRate(rate);
        trade.setExthReward(rewardService.getCurrentReward());
        trade.setStatus(SystemConstants.TradeStatus.MATCHED);
        trade.setMatchTime(LocalDateTime.now());
        trade.setExpireTime(LocalDateTime.now().plusHours(SystemConstants.TradeConstants.TRADE_TIMEOUT_HOURS));
        trade.setPartyAType(getUserType(match.getPartyA()));
        trade.setPartyBType(getUserType(match.getPartyB()));
        trade.setIsPartyAConfirmed(false);
        trade.setIsPartyBConfirmed(false);
        trade.setIsDisputed(false);

        tradeMapper.insert(trade);

        log.info("📝 Trade inserted into database with id={}", trade.getId());

        // ❌ 移除：不再在匹配成功后立即调用链上合约
        // ✅ 改为：等到甲方最终确认时再调用链上合约创建交易对

        // 更新用户类型
        userService.updateUserType(match.getPartyA());
        userService.updateUserType(match.getPartyB());

        // 发送通知
        notificationService.sendTradeNotification(match.getPartyA(), tradeId, "matched");
        notificationService.sendTradeNotification(match.getPartyB(), tradeId, "matched");

        // 缓存交易信息
        cacheTradeInfo(trade);

        log.info("Trade pair created: {} - {} vs {}, amount: {} UT",
                tradeId, match.getPartyA(), match.getPartyB(), match.getAmount());

        return convertToDTO(trade);
    }


    /**
     * A 确认交易
     * 流程：验证 txHash → 更新数据库状态 → 等待链上事件同步
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeDTO confirmPartyA(String address, String tradeId, String txHash) {
        RLock lock = redissonClient.getLock("trade:lock:" + tradeId);
        try {
            if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
            if (trade == null) {
                throw new BusinessException("Trade not found");
            }

            // 验证当前用户是否为甲方
            if (!trade.getPartyA().equals(address)) {
                throw new BusinessException("Only party A can confirm");
            }

            // 检查状态 - 必须是已匹配状态
            if (trade.getStatus() != SystemConstants.TradeStatus.MATCHED) {
                throw new BusinessException("Trade status is not matched");
            }

            // 验证甲方的转账（链上验证）
            String exthContractAddress = blockchainService.getExthContractAddress();
            boolean verified = blockchainService.verifyTransaction(txHash,
                    address, exthContractAddress, trade.getAmountA());
            if (!verified) {
                throw new BusinessException("Transaction verification failed");
            }

            // 甲方确认后，状态变为 PARTY_A_CONFIRMED（等待乙方确认）
            // 注意：这里只更新数据库，不调用链上合约
            trade.setIsPartyAConfirmed(true);
            trade.setPartyAConfirmTime(LocalDateTime.now());
            trade.setStatus(SystemConstants.TradeStatus.PARTY_A_CONFIRMED);
            trade.setTxHashA(txHash);
            tradeMapper.updateById(trade);

            // 发送通知
            notificationService.sendTradeNotification(trade.getPartyB(), tradeId, "party_a_confirmed");

            log.info("Party A confirmed: {}, trade: {}", address, tradeId);

            return convertToDTO(trade);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500,"System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    /**
     * B 确认交易
     * 流程：验证 txHash → 更新数据库状态 → 等待链上事件同步
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeDTO confirmPartyB(String address, String tradeId, String txHash) {
        RLock lock = redissonClient.getLock("trade:lock:" + tradeId);
        try {
            if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
            if (trade == null) {
                throw new BusinessException("Trade not found");
            }

            if (!trade.getPartyB().equals(address)) {
                throw new BusinessException("Only party B can confirm");
            }

            if (trade.getStatus() != SystemConstants.TradeStatus.PARTY_A_CONFIRMED) {
                throw new BusinessException("Party A has not confirmed yet");
            }

            if (LocalDateTime.now().isAfter(trade.getExpireTime())) {
                trade.setStatus(SystemConstants.TradeStatus.EXPIRED);
                tradeMapper.updateById(trade);
                throw new BusinessException("Trade expired");
            }

            // 验证乙方的转账（链上验证）
            String exthContractAddress = blockchainService.getExthContractAddress();
            boolean verified = blockchainService.verifyTransaction(txHash,
                    address, exthContractAddress, trade.getAmountB());
            if (!verified) {
                throw new BusinessException("Transaction verification failed");
            }

            // 乙方确认了，但不直接完成交易，而是等待甲方最终确认
            // 注意：这里只更新数据库，不调用链上合约
            trade.setIsPartyBConfirmed(true);
            trade.setPartyBConfirmTime(LocalDateTime.now());
            trade.setTxHashB(txHash);
            trade.setStatus(SystemConstants.TradeStatus.PARTY_B_CONFIRMED);
            tradeMapper.updateById(trade);

            // 发送通知给甲方，让其进行最终确认
            notificationService.sendTradeNotification(trade.getPartyA(), tradeId, "party_b_confirmed");

            log.info("Party B confirmed: {}, trade: {}", address, tradeId);

            return convertToDTO(trade);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Operation interrupted");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    /**
     * 甲方最终确认乙方的转账，完成交易
     * 关键改进：只调用链上合约，不直接更新数据库状态
     * 状态更新由 BlockchainEventListener 监听事件后同步
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeDTO finalConfirmPartyA(String address, String tradeId) {
        RLock lock = redissonClient.getLock("trade:lock:" + tradeId);
        try {
            if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
            if (trade == null) {
                throw new BusinessException("Trade not found");
            }

            // 验证当前用户是否为甲方
            if (!trade.getPartyA().equals(address)) {
                throw new BusinessException("Only party A can final confirm");
            }

            // 检查状态 - 必须是乙方已确认状态
            if (trade.getStatus() != SystemConstants.TradeStatus.PARTY_B_CONFIRMED) {
                throw new BusinessException("Party B has not confirmed yet");
            }

            // ✅ 改进 1：在链上创建交易对并获取 chainTradeId
            BigInteger chainTradeId = null;
            try {
                log.info("🔗 Creating trade pair on chain for: {} vs {}, amount: {}",
                        trade.getPartyA(), trade.getPartyB(), trade.getAmount());

                String chainTradeIdStr = blockchainService.createTradePairOnChain(
                        trade.getPartyA(),
                        trade.getPartyB(),
                        trade.getAmount()
                );

                log.info("✅ Trade pair created on chain: chainTradeIdStr={}", chainTradeIdStr);

                // 解析 chainTradeId
                if (chainTradeIdStr != null && !chainTradeIdStr.startsWith("0x") && chainTradeIdStr.matches("\\d+")) {
                    chainTradeId = new BigInteger(chainTradeIdStr);

                    // 保存到数据库
                    trade.setChainTradeId(chainTradeId.longValue());
                    log.info("✅ Saved chainTradeId to database: {}", chainTradeId);
                } else {
                    log.warn("⚠️ Invalid chainTradeId format: {}", chainTradeIdStr);
                }

            } catch (Exception e) {
                log.error("❌ Failed to create trade pair on chain: {}", e.getMessage(), e);
                throw new BusinessException("Failed to create trade pair on chain: " + e.getMessage());
            }

            // ✅ 改进 2：使用 chainTradeId 调用链上合约完成交易
            if (chainTradeId == null) {
                throw new BusinessException("Failed to get chainTradeId");
            }

            String txHash = blockchainService.completeTrade(chainTradeId);
            log.info("✅ Trade completed on chain: txHash={}", txHash);

            // ✅ 改进 3：设置待确认状态，等待事件同步
            trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_CONFIRM);
            trade.setChainTxHash(txHash);
            trade.setCompleteTime(LocalDateTime.now());

            // ✅ 新增：减少甲方的新用户剩余次数（如果甲方是 NEW 用户且有剩余次数）
            UserEntity partyA = userMapper.selectByAddress(trade.getPartyA());
            if (partyA != null && partyA.getUserType() == SystemConstants.UserType.NEW
                    && partyA.getNewUserTradeCount() > 0) {

                partyA.setNewUserTradeCount(partyA.getNewUserTradeCount() - 1);

                // 如果次数用完，升级为 NORMAL
                if (partyA.getNewUserTradeCount() == 0) {
                    partyA.setUserType(SystemConstants.UserType.NORMAL);
                    log.info("User upgraded to NORMAL after completing 3 trades: {}", trade.getPartyA());
                }

                userMapper.updateById(partyA);

                // 清除缓存
                String cacheKey = SystemConstants.RedisKey.USER_INFO + trade.getPartyA();
                redissonClient.getBucket(cacheKey).delete();

                log.info("Decremented newUserTradeCount for partyA: {}", trade.getPartyA());
            }

            tradeMapper.updateById(trade);

            // ✅ 改进 4：收取手续费（链上操作）
            BigDecimal fee = AmountUtil.calculateFee(trade.getAmount(),
                    SystemConstants.TradeConstants.FEE_RATE,
                    SystemConstants.TradeConstants.FEE_DENOMINATOR);
            blockchainService.collectFee(chainTradeId, fee.toBigInteger());

            // ✅ 改进 5：清除缓存，让事件监听器重新同步
            String cacheKey = SystemConstants.RedisKey.TRADE_INFO + tradeId;
            redissonClient.getBucket(cacheKey).delete();

            // ✅ 改进 6：发送通知（但不更新状态）
            notificationService.sendTradeNotification(trade.getPartyA(), tradeId, "pending_chain_confirm");
            notificationService.sendTradeNotification(trade.getPartyB(), tradeId, "pending_chain_confirm");

            log.info("Final confirm submitted to chain: {}, trade: {}, txHash: {}",
                    address, tradeId, txHash);

            return convertToDTO(trade);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500,"System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 创建争议
     * 流程：先更新链下数据库 → 再调用链上合约（如果需要赔偿）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DisputeDTO disputeTrade(String address, DisputeParam param) {
        TradeRecordEntity trade = tradeMapper.selectByTradeId(param.getTradeId());
        if (trade == null) {
            throw new BusinessException("Trade not found");
        }

        // 验证发起方是交易参与方
        if (!trade.getPartyA().equals(address) && !trade.getPartyB().equals(address)) {
            throw new BusinessException("Not a party to this trade");
        }

        // 验证交易状态
        if (trade.getStatus() == SystemConstants.TradeStatus.COMPLETED) {
            throw new BusinessException("Cannot dispute completed trade");
        }

        // 确定被争议方
        String accused = trade.getPartyA().equals(address) ? trade.getPartyB() : trade.getPartyA();

        // 创建争议记录（链下）
        DisputeRecordEntity dispute = new DisputeRecordEntity();
        dispute.setTradeId(param.getTradeId());
        dispute.setInitiator(address);
        dispute.setAccused(accused);
        dispute.setReason(param.getReason());
        dispute.setEvidence(param.getEvidence());
        dispute.setStatus(SystemConstants.DisputeStatus.PENDING);

        disputeMapper.insert(dispute);

        // 更新交易状态（链下）
        trade.setIsDisputed(true);
        trade.setDisputedParty(accused);
        trade.setStatus(SystemConstants.TradeStatus.DISPUTED);
        tradeMapper.updateById(trade);

        // 发送通知
        notificationService.sendTradeNotification(accused, param.getTradeId(), "disputed");

        log.info("Dispute created: trade {}, initiator: {}", param.getTradeId(), address);

        return convertToDisputeDTO(dispute);
    }

    /**
     * 解决争议
     * 流程：先更新链下数据库 → 再调用链上 Treasure 合约赔偿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DisputeDTO resolveDispute(Long disputeId, ResolveDisputeParam param) {
        DisputeRecordEntity dispute = disputeMapper.selectById(disputeId);
        if (dispute == null) {
            throw new BusinessException("Dispute not found");
        }

        // 更新争议状态（链下）
        dispute.setStatus(param.getStatus());
        dispute.setResult(param.getResult());
        dispute.setResolveTime(LocalDateTime.now());
        disputeMapper.updateById(dispute);

        TradeRecordEntity trade = tradeMapper.selectByTradeId(dispute.getTradeId());

        if (param.getStatus() == SystemConstants.DisputeStatus.BLACKLISTED) {
            // 拉黑被争议方（先链下，后链上）
            userService.blacklistUser(dispute.getAccused(), "Dispute resolved: " + param.getResult());

            // 从金库 Treasure 赔偿损失方（链上操作）
            try {
                // 赔偿金额
                BigDecimal compensationAmount = trade.getAmount();

                // 获取 USDT 合约地址
                String usdtAddress = blockchainService.getUsdtContractAddress();

                // 从链上执行赔偿
                String txHash = blockchainService.compensateERC20FromTreasure(
                        usdtAddress,                    // USDT 代币合约地址
                        dispute.getInitiator(),         // 受害者地址
                        compensationAmount.toBigInteger()  // 赔偿金额
                );

                log.info("Compensation paid from Treasure: txHash={}, amount={}", txHash, compensationAmount);

                // 记录赔偿信息到争议记录表
                dispute.setResult(param.getResult() + " [已赔偿：" + compensationAmount + ", TX: " + txHash + "]");
                disputeMapper.updateById(dispute);

            } catch (Exception e) {
                log.error("Failed to pay compensation from Treasure for dispute {}: {}",
                        disputeId, e.getMessage());
                // 赔偿失败不影响争议状态
            }
        }

        // 更新交易状态（取消）
        trade.setStatus(SystemConstants.TradeStatus.CANCELLED);
        tradeMapper.updateById(trade);

        log.info("Dispute resolved: {}, status: {}", disputeId, param.getStatus());

        return convertToDisputeDTO(dispute);
    }


    /**
     * 获取交易详情
     */
    @Override
    public TradeDTO getTradeDetail(String tradeId) {
        // 优先从缓存获取
        String cacheKey = SystemConstants.RedisKey.TRADE_INFO + tradeId;
        RBucket<TradeDTO> bucket = redissonClient.getBucket(cacheKey);
        TradeDTO cached = bucket.get();
        if (cached != null) {
            return cached;
        }

        TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
        if (trade == null) {
            throw new BusinessException("Trade not found");
        }

        TradeDTO dto = convertToDTO(trade);
        bucket.set(dto, 10, TimeUnit.MINUTES);

        return dto;
    }

    /**
     * 获取用户交易列表
     */
    @Override
    public List<TradeDTO> getUserTrades(String address, Integer status) {
        Page<TradeRecordEntity> pageParam = new PageDTO<>();

        LambdaQueryWrapper<TradeRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(TradeRecordEntity::getPartyA, address)
                .or().eq(TradeRecordEntity::getPartyB, address));

        if (status != null) {
            wrapper.eq(TradeRecordEntity::getStatus, status);
        }

        wrapper.orderByDesc(TradeRecordEntity::getCreateTime);

        Page<TradeRecordEntity> pageResult = tradeMapper.selectPage(pageParam, wrapper);

        return pageResult.getRecords().stream()
                .map(entity -> {
                    TradeDTO dto = convertToDTO(entity);
                    dto
                            .setMyRole(address.equals(entity.getPartyA()) ? "partyA" : "partyB");
                    return dto;
                })
                .collect(Collectors.toList());
    }
    /**
     * 检查过期交易
     */
    @Override
    public void checkExpiredTrades() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<TradeRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(TradeRecordEntity::getExpireTime, now)
                .in(TradeRecordEntity::getStatus,
                        SystemConstants.TradeStatus.MATCHED,
                        SystemConstants.TradeStatus.PARTY_A_CONFIRMED);

        List<TradeRecordEntity> expiredTrades = tradeMapper.selectList(wrapper);

        for (TradeRecordEntity trade : expiredTrades) {
            trade.setStatus(SystemConstants.TradeStatus.EXPIRED);
            tradeMapper.updateById(trade);

            log.info("Trade expired: {}", trade.getTradeId());

            // 发送通知
            notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "expired");
            notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "expired");
        }
    }

    /**
     * 更新交易状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String tradeId, Integer status) {
        TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
        if (trade == null) {
            log.warn("Trade not found for status update: {}", tradeId);
            return;
        }

        Integer oldStatus = trade.getStatus();
        trade.setStatus(status);
        tradeMapper.updateById(trade);

        // 清除缓存
        String cacheKey = SystemConstants.RedisKey.TRADE_INFO + tradeId;
        redissonClient.getBucket(cacheKey).delete();

        log.info("Trade {} status updated from {} to {}", tradeId, oldStatus, status);
    }

    /**
     * 生成交易ID
     */
    private String generateTradeId() {
        return "T" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取用户类型
     */
    private Integer getUserType(String address) {
        UserEntity user = userMapper.selectByAddress(address);
        return user != null ? user.getUserType() : SystemConstants.UserType.NORMAL;
    }

    /**
     * 缓存交易信息
     */
    private void cacheTradeInfo(TradeRecordEntity trade) {
        String cacheKey = SystemConstants.RedisKey.TRADE_INFO + trade.getTradeId();
        TradeDTO dto = convertToDTO(trade);
        redissonClient.getBucket(cacheKey).set(dto, 10, TimeUnit.MINUTES);
    }

    /**
     * 转换为交易DTO
     */
    private TradeDTO convertToDTO(TradeRecordEntity entity) {
        if (entity == null) return null;

        TradeDTO dto = new TradeDTO();
        dto.setTradeId(entity.getTradeId());
        dto.setPartyA(entity.getPartyA());
        dto.setPartyB(entity.getPartyB());
        dto.setPartyAType(entity.getPartyAType());
        dto.setPartyBType(entity.getPartyBType());
        dto.setAmount(entity.getAmount());
        dto.setAmountA(entity.getAmountA());
        dto.setAmountB(entity.getAmountB());
        dto.setFromCurrency(entity.getFromCurrency());
        dto.setToCurrency(entity.getToCurrency());
        dto.setExchangeRate(entity.getExchangeRate());
        dto.setExthReward(entity.getExthReward());
        dto.setStatus(entity.getStatus());
        dto.setStatusDesc(getTradeStatusDesc(entity.getStatus()));
        dto.setIsPartyAConfirmed(entity.getIsPartyAConfirmed());
        dto.setIsPartyBConfirmed(entity.getIsPartyBConfirmed());
        dto.setMatchTime(entity.getMatchTime());
        dto.setExpireTime(entity.getExpireTime());
        dto.setIsDisputed(entity.getIsDisputed());
        dto.setDisputedParty(entity.getDisputedParty());
        dto.setTxHashA(entity.getTxHashA());
        dto.setTxHashB(entity.getTxHashB());
        dto.setChainTradeId(entity.getChainTradeId());

        return dto;
    }
    /**
     * 获取交易状态描述
     */
    private String getTradeStatusDesc(int status) {
        switch (status) {
            case 0: return "等待匹配";
            case 1: return "已匹配";
            case 2: return "A方已转账";
            case 3: return "B方已确认";
            case 4: return "已完成";
            case 5: return "争议中";
            case 6: return "已过期";
            case 7: return "已取消";
            default: return "未知";
        }
    }

    /**
     * 转换为争议DTO
     */
    private DisputeDTO convertToDisputeDTO(DisputeRecordEntity entity) {
        if (entity == null) return null;

        DisputeDTO dto = new DisputeDTO();
        dto.setId(entity.getId());
        dto.setTradeId(entity.getTradeId());
        dto.setInitiator(entity.getInitiator());
        dto.setAccused(entity.getAccused());
        dto.setReason(entity.getReason());
        dto.setEvidence(entity.getEvidence());
        dto.setStatus(entity.getStatus());
        dto.setStatusDesc(getDisputeStatusDesc(entity.getStatus()));
        dto.setResult(entity.getResult());
        dto.setCreateTime(entity.getCreateTime());
        dto.setResolveTime(entity.getResolveTime());

        return dto;
    }

    /**
     * 获取争议状态描述
     */
    private String getDisputeStatusDesc(int status) {
        switch (status) {
            case 0: return "待处理";
            case 1: return "已解决";
            case 2: return "已驳回";
            case 3: return "已拉黑";
            default: return "未知";
        }
    }
}
