package com.mnnu.service;


import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * 空投服务接口
 */



public interface AirdropService {

    @Transactional(rollbackFor = Exception.class)
    AirdropDTO claimAirdrop(String address, BigInteger amount, List<byte[]> merkleProof);

    /**
     * 检查是否已领取
     */
    boolean hasClaimed(String address);

    /**
     * 获取空投信息
     */
    AirdropInfoDTO getAirdropInfo(String address);

    /**
     * 初始化空投
     */
    void initAirdrop(BigDecimal totalAmount, BigDecimal perAddress);

    /**
     * 标记用户已领取空投（用于链上事件处理）
     */
    void markAsClaimed(String address);
}
