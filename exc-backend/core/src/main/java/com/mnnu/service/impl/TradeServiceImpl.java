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
import com.mnnu.mapper.TradeMapper;
import com.mnnu.mapper.UserMapper;
import com.mnnu.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final TradeMapper tradeMapper;
    private final DisputeRecordMapper disputeMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final RateService rateService;
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
        trade.setPartyAType(getUserType(match.getPartyA()));
        trade.setPartyBType(getUserType(match.getPartyB()));
        trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_COMPLETE);

        tradeMapper.insert(trade);

        log.info("Trade inserted into database with id={}", trade.getId());

        return convertToDTO(trade);
    }


    /**
     * A 确认交易
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
            if (LocalDateTime.now().isAfter(trade.getExpireTime())) {
                trade.setStatus(SystemConstants.TradeStatus.DISPUTED);
                tradeMapper.updateById(trade);
                throw new BusinessException("Trade expired");
            }

            // 验证甲方的转账（链上验证）
            String exthContractAddress = blockchainService.getExthContractAddress();
            boolean verified = blockchainService.verifyTransaction(txHash,
                    address, exthContractAddress, trade.getAmountA());
            if (!verified) {
                throw new BusinessException("Transaction verification failed");
            }

            trade.setTxHashA(txHash);
            trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_COMPLETE);
            tradeMapper.updateById(trade);


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
                trade.setStatus(SystemConstants.TradeStatus.DISPUTED);
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

            trade.setTxHashB(txHash);
            trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_COMPLETE);

            tradeMapper.updateById(trade);

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
            lock.lock();

            TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
            if (trade == null) {
                throw new BusinessException("Trade not found");
            }

            //校验权限和状态
            if (!trade.getPartyA().equals(address)) {
                throw new BusinessException("Only Party A can finalize the trade");
            }

            // 此时状态应该是 2 (PartyB Confirmed)，允许发起最终确认
            if (trade.getStatus() != SystemConstants.TradeStatus.PARTY_B_CONFIRMED) {
                throw new BusinessException("Trade is not ready to be completed. Current status: " + trade.getStatus());
            }
            if (LocalDateTime.now().isAfter(trade.getExpireTime())) {
                trade.setStatus(SystemConstants.TradeStatus.DISPUTED);
                tradeMapper.updateById(trade);
                throw new BusinessException("Trade expired");
            }

            // 更新本地记录（标记已提交，具体状态由 Complete 事件监听器更新）
            trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_COMPLETE);
            tradeMapper.updateById(trade);

            return convertToDTO(trade);

        } catch (Exception e) {
            log.error("Failed to finalize trade", e);
            throw new BusinessException("Finalize failed: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 取消交易
     * 流程：更新链下状态 → 调用链上合约取消 → 监听器同步最终状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeDTO cancelTrade(String address, String tradeId) {
        RLock lock = redissonClient.getLock("trade:lock:" + tradeId);
        try {
            if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
            if (trade == null) {
                throw new BusinessException("Trade not found");
            }

            // 验证权限：只有交易参与方可以取消
            if (!trade.getPartyA().equals(address) && !trade.getPartyB().equals(address)) {
                throw new BusinessException("Not a party to this trade");
            }

            // 验证状态：只有特定状态可以取消
            if (trade.getStatus() == SystemConstants.TradeStatus.COMPLETED) {
                throw new BusinessException("Cannot cancel completed trade");
            }
            if (trade.getStatus() == SystemConstants.TradeStatus.CANCELLED) {
                throw new BusinessException("Trade already cancelled");
            }
            if (trade.getStatus() == SystemConstants.TradeStatus.DISPUTED) {
                throw new BusinessException("Cannot cancel disputed trade");
            }
            if (trade.getStatus() == SystemConstants.TradeStatus.EXPIRED) {
                throw new BusinessException("Cannot cancel expired trade");
            }
            // 更新链下状态为待取消（等待链上确认）
            trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_COMPLETE);
            tradeMapper.updateById(trade);

            log.info("Trade {} cancellation initiated by {}", tradeId, address);

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
     * 创建争议
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


        if (trade.getStatus() == SystemConstants.TradeStatus.DISPUTED) {
            throw new BusinessException("Dispute already submit");
        }

        if (trade.getStatus() == SystemConstants.TradeStatus.RESOLVED) {
            throw new BusinessException("Dispute already resolve");
        }

        // 确定被争议方
        String accused = trade.getPartyA().equals(address) ? trade.getPartyB() : trade.getPartyA();

        trade.setStatus(SystemConstants.TradeStatus.PENDING_CHAIN_COMPLETE);
        trade.setDisputeStatus(SystemConstants.DisputeStatus.PENDING_CHAIN_COMPLETE);
        tradeMapper.updateById(trade);

        // 创建争议记录（链下）
        DisputeRecordEntity dispute = new DisputeRecordEntity();
        dispute.setTradeId(param.getTradeId());
        dispute.setInitiator(address);
        dispute.setAccused(accused);
        dispute.setReason(param.getReason());
        dispute.setEvidence(param.getEvidence());
        dispute.setProposalStatus(SystemConstants.DisputeStatus.PENDING_CHAIN_COMPLETE);

        disputeMapper.insert(dispute);


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
        dispute.setProposalStatus(SystemConstants.DisputeStatus.PENDING_CHAIN_COMPLETE);
        disputeMapper.updateById(dispute);

        return convertToDisputeDTO(dispute);
    }



    /**
     * 获取交易详情
     */
    @Override
    public TradeDTO getTradeDetail(String tradeId) {

        TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
        if (trade == null) {
            throw new BusinessException("Trade not found");
        }
        TradeDTO dto = convertToDTO(trade);

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
                    dto.setMyRole(address.equals(entity.getPartyA()) ? "partyA" : "partyB");
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
            trade.setStatus(SystemConstants.TradeStatus.DISPUTED);
            tradeMapper.updateById(trade);

            log.info("Trade expired: {}", trade.getTradeId());

            // 发送通知
            notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "expired");
            notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "expired");
        }

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
        return  user.getUserType();
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
        dto.setMatchTime(entity.getMatchTime());
        dto.setExpireTime(entity.getExpireTime());
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
            case 6: return "争议解决";
            case 7: return "交易取消";
            case 8: return "等待链上确认";
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
        dto.setStatus(entity.getProposalStatus());
        dto.setStatusDesc(getDisputeStatusDesc(entity.getProposalStatus()));
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
            case 0: return "等待链上确认";
            case 1: return "处理中";
            case 2: return "已解决";
            case 3: return "已驳回";
            default: return "未知";
        }
    }
}
