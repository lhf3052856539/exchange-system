package com.mnnu.dto;
/**
 * 等待队列统计DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class WaitingQueueStatsDTO implements Serializable {
    private Integer waitingCount;
    private List<QueueItemDTO> waitingList;
}
