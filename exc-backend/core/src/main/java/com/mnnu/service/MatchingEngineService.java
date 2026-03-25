package com.mnnu.service;
/**
 * 匹配引擎服务
 */

import com.mnnu.dto.QueueItemDTO;
import com.mnnu.dto.TradeMatchDTO;
import com.mnnu.dto.WaitingQueueStatsDTO;

import java.util.List;

public interface MatchingEngineService {

    /**
     * 添加等待队列
     */
    void addToWaitingQueue(String address, Long amount, String fromCurrency, String toCurrency);

    /**
     * 从等待队列移除
     */
    void removeFromWaitingQueue(String address);

    /**
     * 执行匹配
     */
    List<TradeMatchDTO> executeMatching();

    /**
     * 获取等待队列统计
     */
    WaitingQueueStatsDTO getWaitingQueueStats();

    /**
     * 获取用户在队列中的位置
     */
    default Integer getUserQueuePosition(String address, WaitingQueueStatsDTO stats) {
        if (stats == null || stats.getWaitingList() == null) {
            return 1;
        }

        List<QueueItemDTO> waitingList = stats.getWaitingList();
        for (int i = 0; i < waitingList.size(); i++) {
            if (address.equals(waitingList.get(i).getAddress())) {
                return i + 1;
            }
        }

        return 1;
    }


    /**
     * 随机选择率先转账方
     */
    String selectRandomFirstParty(String partyA, String partyB);
}
