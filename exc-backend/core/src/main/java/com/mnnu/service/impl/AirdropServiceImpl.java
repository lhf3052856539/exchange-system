package com.mnnu.service.impl;

import com.mnnu.exception.BusinessException;
import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import com.mnnu.entity.AirdropEntity;
import com.mnnu.mapper.AirdropMapper;
import com.mnnu.service.AirdropService;
import com.mnnu.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 空投服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirdropServiceImpl implements AirdropService {

    private final AirdropMapper airdropMapper;
    private final BlockchainService blockchainService;

    /**
     * 领取空投
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AirdropDTO claimAirdrop(String address, BigInteger amount, List<byte[]> merkleProof) {
        // 检查是否已领取
        if (hasClaimed(address)) {
            throw new BusinessException("Airdrop already claimed");
        }

        // 获取空投配置
        AirdropEntity airdrop = airdropMapper.selectLatest();
        if (airdrop == null || !airdrop.getIsActive()) {
            throw new BusinessException("No active airdrop available");
        }

        // 创建领取记录（Merkle 模式下金额由链上验证）
        AirdropEntity record = new AirdropEntity();
        record.setAddress(address);
        record.setAmount(BigDecimal.ZERO); // 金额为 0，实际由链上控制
        record.setHasClaimed(true);
        record.setClaimTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());

        airdropMapper.insert(record);

        // 调用链上空投合约（Merkle 验证在链上进行）
        try {
            blockchainService.claimAirdropOnChain(address, amount, merkleProof);
        } catch (Exception e) {
            log.error("Failed to claim airdrop on chain", e);
            throw new BusinessException("Failed to claim airdrop on chain: " + e.getMessage());
        }

        log.info("Airdrop claimed successfully via Merkle proof: {}", address);
        return convertToDTO(record);
    }


    /**
     * 检查是否已领取
     */
    @Override
    public boolean hasClaimed(String address) {
        AirdropEntity entity = airdropMapper.selectByAddress(address);
        return entity != null && Boolean.TRUE.equals(entity.getHasClaimed());
    }

    /**
     * 获取空投信息
     */
    @Override
    public AirdropInfoDTO getAirdropInfo(String address) {
        AirdropInfoDTO info = new AirdropInfoDTO();
        info.setHasClaimed(hasClaimed(address));

        AirdropEntity latest = airdropMapper.selectLatest();
        if (latest != null && latest.getIsActive()) {
            info.setTotalAirdrop(latest.getTotalAmount());
            info.setPerAddress(BigDecimal.ZERO); // Merkle 模式下每个人金额不同
            info.setClaimedAmount(BigDecimal.ZERO);
            info.setRemainingAmount(latest.getTotalAmount());
            info.setMyClaimed(BigDecimal.ZERO);

            // 前端需要的字段
            info.setTotalAirdropAmount(latest.getTotalAmount());
            info.setFixedAmount(BigDecimal.ZERO);
            info.setIsActive(true);
        } else {
            info.setTotalAirdrop(BigDecimal.ZERO);
            info.setPerAddress(BigDecimal.ZERO);
            info.setClaimedAmount(BigDecimal.ZERO);
            info.setRemainingAmount(BigDecimal.ZERO);
            info.setMyClaimed(BigDecimal.ZERO);
            info.setTotalAirdropAmount(BigDecimal.ZERO);
            info.setFixedAmount(BigDecimal.ZERO);
            info.setIsActive(false);
        }

        return info;
    }

    /**
     * 初始化空投
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initAirdrop(BigDecimal totalAmount, BigDecimal perAddress) {
        AirdropEntity airdrop = new AirdropEntity();
        airdrop.setTotalAmount(totalAmount);
        airdrop.setPerAddressAmount(perAddress);
        airdrop.setIsActive(true);
        airdrop.setStartTime(LocalDateTime.now());
        airdrop.setCreateTime(LocalDateTime.now());

        airdropMapper.insert(airdrop);
        log.info("Airdrop initialized: total={}, perAddress={}", totalAmount, perAddress);
    }

    /**
     * 标记为已领取
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsClaimed(String address) {
        try {
            AirdropEntity entity = airdropMapper.selectByAddress(address);

            if (entity == null) {
                entity = new AirdropEntity();
                entity.setAddress(address);
                entity.setHasClaimed(true);
                entity.setClaimTime(LocalDateTime.now());
                entity.setCreateTime(LocalDateTime.now());
                airdropMapper.insert(entity);
            } else if (!Boolean.TRUE.equals(entity.getHasClaimed())) {
                entity.setHasClaimed(true);
                entity.setClaimTime(LocalDateTime.now());
                airdropMapper.updateById(entity);
            }

            log.debug("Marked airdrop as claimed for user: {}", address);
        } catch (Exception e) {
            log.error("Failed to mark airdrop as claimed for {}: {}", address, e.getMessage());
        }
    }


    /**
     * 转换为 DTO
     */
    private AirdropDTO convertToDTO(AirdropEntity entity) {
        if (entity == null) return null;

        AirdropDTO dto = new AirdropDTO();
        dto.setAddress(entity.getAddress());
        dto.setAmount(entity.getAmount());
        dto.setTxHash("");
        dto.setClaimTime(entity.getClaimTime());

        return dto;
    }
}
