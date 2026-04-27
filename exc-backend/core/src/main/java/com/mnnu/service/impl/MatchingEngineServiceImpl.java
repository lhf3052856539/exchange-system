package com.mnnu.service.impl;

import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.QueueItemDTO;
import com.mnnu.dto.TradeMatchDTO;
import com.mnnu.dto.WaitingQueueStatsDTO;
import com.mnnu.service.MatchingEngineService;
import com.mnnu.service.TradeService;
import com.mnnu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchingEngineServiceImpl implements MatchingEngineService {
    @Autowired
    private UserService userService;

    // 内存中的等待队列
    private final ConcurrentHashMap<String, QueueItem> waitingQueue = new ConcurrentHashMap<>();

    /**
     * 添加到等待队列
     */
    @Override
    public void addToWaitingQueue(String address, Long amount, String fromCurrency, String toCurrency) {
        if (waitingQueue.containsKey(address)) {
            log.warn("Address {} already in waiting queue", address);
            return;
        }

        QueueItem item = new QueueItem(address, amount, fromCurrency, toCurrency, System.currentTimeMillis());
        waitingQueue.put(address, item);

        log.info("Added {} to waiting queue: {} {} -> {}", address, amount, fromCurrency, toCurrency);
    }

    /**
     * 从等待队列移除
     */
    @Override
    public void removeFromWaitingQueue(String address) {
        waitingQueue.remove(address);
        log.info("Removed {} from waiting queue", address);
    }

    /**
     * 执行匹配逻辑
     */
    @Override
    public List<TradeMatchDTO> executeMatching() {
        log.info("========== 开始执行匹配引擎 ==========");
        log.info("当前等待队列大小: {}", waitingQueue.size());

        List<TradeMatchDTO> matches = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>();

        // 按币种组合分组，key 格式："from-to"
        Map<String, List<QueueItem>> currencyGroups = waitingQueue.entrySet().stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.groupingBy(item -> item.fromCurrency + "-" + item.toCurrency));

        log.info("币种组合分组情况:");
        for (Map.Entry<String, List<QueueItem>> entry : currencyGroups.entrySet()) {
            log.info("  组合 {}: {} 个请求", entry.getKey(), entry.getValue().size());
            for (QueueItem item : entry.getValue()) {
                log.info("    - 地址: {}, 金额: {}, 时间: {}",
                        item.address, item.amount, new java.util.Date(item.timestamp));
            }
        }

        // 遍历每个币种组合
        for (Map.Entry<String, List<QueueItem>> entry : currencyGroups.entrySet()) {
            String currencyPair = entry.getKey();

            // 如果已经处理过这个组合或其反向组合，跳过
            if (processedPairs.contains(currencyPair)) {
                log.debug("跳过已处理的组合: {}", currencyPair);
                continue;
            }

            List<QueueItem> groupA = entry.getValue();

            if (groupA.isEmpty()) {
                processedPairs.add(currencyPair);
                continue;
            }

            // 计算反向币种组合，例如 "RNB-GBP" 的反向是 "GBP-RNB"
            String[] parts = currencyPair.split("-");
            String reversePair = parts[1] + "-" + parts[0];
            List<QueueItem> groupB = currencyGroups.get(reversePair);

            log.info("尝试匹配组合: {} vs {}", currencyPair, reversePair);

            if (groupB == null || groupB.isEmpty()) {
                log.warn("没有找到反向组合 {} 的匹配请求", reversePair);
                processedPairs.add(currencyPair);
                continue;
            }

            log.info("找到 {} 个可匹配的反向请求", groupB.size());

            // 从两个组中两两配对
            int i = 0, j = 0;
            while (i < groupA.size() && j < groupB.size()) {
                QueueItem itemA = groupA.get(i);
                QueueItem itemB = groupB.get(j);

                log.info("比较请求: A({} {}->{}) vs B({} {}->{})",
                        itemA.address, itemA.amount, itemA.fromCurrency + "->" + itemA.toCurrency,
                        itemB.address, itemB.amount, itemB.fromCurrency + "->" + itemB.toCurrency);

                // 检查金额是否匹配
                if (itemA.amount.equals(itemB.amount)) {
                    log.info("✓ 金额匹配: {}", itemA.amount);

                    // 检查用户类型，不允许两个新用户匹配
                    int userTypeA = getUserType(itemA.address);
                    int userTypeB = getUserType(itemB.address);

                    log.info("用户类型: A(type={}) vs B(type={})", userTypeA, userTypeB);

                    if (userTypeA == SystemConstants.UserType.NEW && userTypeB == SystemConstants.UserType.NEW) {
                        // 两个都是新用户，跳过这次匹配
                        log.info("✗ 跳过匹配: 两个用户都是新用户 - {} vs {}",
                                itemA.address, itemB.address);

                        // 跳过金额较小的一方，让另一方继续等待合适的匹配
                        if (i < groupA.size() - 1) {
                            i++;
                        } else {
                            j++;
                        }
                        continue;
                    }

                    // 可以匹配，创建交易对
                    TradeMatchDTO match = createMatch(itemA, itemB, userTypeA, userTypeB);
                    matches.add(match);

                    // 从队列移除
                    waitingQueue.remove(itemA.address);
                    waitingQueue.remove(itemB.address);

                    log.info("✅ 成功匹配: {}(type={}) vs {}(type={}), amount: {}",
                            itemA.address, getUserTypeDesc(userTypeA),
                            itemB.address, getUserTypeDesc(userTypeB),
                            itemA.amount);

                    i++;
                    j++;
                } else {
                    log.info("✗ 金额不匹配: A={} vs B={}", itemA.amount, itemB.amount);
                    if (itemA.amount.compareTo(itemB.amount) < 0) {
                        // A 的金额较小，跳过 A
                        log.info("  → A金额较小，跳过A");
                        i++;
                    } else {
                        // B 的金额较小，跳过 B
                        log.info("  → B金额较小，跳过B");
                        j++;
                    }
                }
            }

            // 标记这两个组合都已处理
            processedPairs.add(currencyPair);
            processedPairs.add(reversePair);
        }

        log.info("========== 匹配引擎执行完成，共找到 {} 个匹配 ==========", matches.size());
        return matches;
    }

    /**
     * 获取等待队列统计
     */
    @Override
    public WaitingQueueStatsDTO getWaitingQueueStats() {
        List<QueueItemDTO> waitingList = waitingQueue.values().stream()
                .map(item -> {
                    QueueItemDTO dto = new QueueItemDTO();
                    dto.setAddress(item.address);
                    dto.setAmount(item.amount);
                    dto.setFromCurrency(item.fromCurrency);
                    dto.setToCurrency(item.toCurrency);
                    dto.setWaitTime((System.currentTimeMillis() - item.timestamp) / 1000);
                    return dto;
                })
                .sorted(Comparator.comparingLong(QueueItemDTO::getWaitTime))
                .collect(Collectors.toList());

        WaitingQueueStatsDTO stats = new WaitingQueueStatsDTO();
        stats.setWaitingCount(waitingList.size());
        stats.setWaitingList(waitingList);

        return stats;
    }

    /**
     * 随机选择率先转账方
     */
    @Override
    public String selectRandomFirstParty(String partyA, String partyB) {
        return ThreadLocalRandom.current().nextBoolean() ? partyA : partyB;
    }

    /**
     * 创建交易对匹配
     */
    private TradeMatchDTO createMatch(QueueItem itemA, QueueItem itemB, int userTypeA, int userTypeB) {
        TradeMatchDTO match = new TradeMatchDTO();
        match.setAmount(itemA.amount);

        // 逻辑：确保新用户必须作为率先转账方 (PartyA)
        if (userTypeA == SystemConstants.UserType.NEW) {
            // A 是新用户，必须作为 PartyA
            match.setPartyA(itemA.address);
            match.setPartyB(itemB.address);
            match.setFromCurrency(itemA.fromCurrency);
            match.setToCurrency(itemA.toCurrency);

            log.info("NEW user {} assigned as PartyA", itemA.address);
        } else if (userTypeB == SystemConstants.UserType.NEW) {
            // B 是新用户，必须作为 PartyA
            match.setPartyA(itemB.address);
            match.setPartyB(itemA.address);
            match.setFromCurrency(itemB.fromCurrency);
            match.setToCurrency(itemB.toCurrency);

            log.info("NEW user {} assigned as PartyA", itemB.address);
        } else {
            // 都不是新用户，随机选择
            String firstParty = selectRandomFirstParty(itemA.address, itemB.address);

            if (firstParty.equals(itemA.address)) {
                match.setPartyA(itemA.address);
                match.setPartyB(itemB.address);
                match.setFromCurrency(itemA.fromCurrency);
                match.setToCurrency(itemA.toCurrency);
            } else {
                match.setPartyA(itemB.address);
                match.setPartyB(itemA.address);
                match.setFromCurrency(itemB.fromCurrency);
                match.setToCurrency(itemB.toCurrency);
            }

            log.info("Both NORMAL/SEED users, randomly selected {} as PartyA",
                    match.getPartyA());
        }

        return match;
    }

    /**
     * 获取用户类型
     */
    private int getUserType(String address) {
        try {
            // 从 UserService 获取用户信息
            com.mnnu.dto.UserDTO userInfo = userService.getUserInfo(address);
            if (userInfo != null && userInfo.getUserType() != null) {
                return userInfo.getUserType();
            }
        } catch (Exception e) {
            log.warn("Failed to get user type for {}: {}", address, e.getMessage());
        }
        // 默认返回普通用户
        return SystemConstants.UserType.NORMAL;
    }

    /**
     * 获取用户类型描述
     */
    private String getUserTypeDesc(int type) {
        switch (type) {
            case 0: return "NEW";
            case 1: return "NORMAL";
            case 2: return "SEED";
            default: return "UNKNOWN";
        }
    }


    /**
     * 队列项
     */
    private static class QueueItem {
        String address;
        Long amount;
        String fromCurrency;
        String toCurrency;
        long timestamp;

        QueueItem(String address, Long amount, String fromCurrency, String toCurrency, long timestamp) {
            this.address = address;
            this.amount = amount;
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.timestamp = timestamp;
        }
    }
}
