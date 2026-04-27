package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mnnu.entity.AirdropConfigEntity;
import com.mnnu.entity.AirdropWhitelistEntity;
import com.mnnu.mapper.AirdropConfigMapper;
import com.mnnu.mapper.AirdropWhitelistMapper;
import com.mnnu.service.AirdropService;
import com.mnnu.utils.Web3jUtil;
import com.mnnu.wrapper.AirdropWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static com.mnnu.constant.SystemConstants.RedisKey.AIRDROP_LOCK_KEY_PREFIX;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Service
public class AirdropServiceImpl implements AirdropService {

    @Autowired
    private AirdropConfigMapper configMapper;

    @Autowired
    private AirdropWhitelistMapper whitelistMapper;
    @Autowired
    private AirdropWrapper airdropWrapper;
    @Autowired
    private RedissonClient redissonClient;


    /**
     * 查询用户在当前活跃空投中的权益
     */
    @Override
    public Map<String, Object> getUserClaimInfo(String address) {
        LambdaQueryWrapper<AirdropConfigEntity> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(AirdropConfigEntity::getStatus, 1)
                .orderByDesc(AirdropConfigEntity::getCreateTime)
                .last("LIMIT 1");
        AirdropConfigEntity config = configMapper.selectOne(configWrapper);

        if (config == null) {
            log.warn("No active airdrop config found");
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<AirdropWhitelistEntity> whiteWrapper = new LambdaQueryWrapper<>();
        whiteWrapper.eq(AirdropWhitelistEntity::getConfigId, config.getId())
                .eq(AirdropWhitelistEntity::getAddress, address);
        AirdropWhitelistEntity record = whitelistMapper.selectOne(whiteWrapper);

        BigInteger chainBalance = BigInteger.ZERO;
        try {
            chainBalance = airdropWrapper.getAirdropBalance();
        } catch (Exception e) {
            log.warn("Failed to get airdrop contract balance, using 0", e);
        }

        BigDecimal totalAirdrop = Web3jUtil.fromChainUnit(chainBalance);


        Map<String, Object> result = new HashMap<>();
        result.put("isActive", true);
        result.put("merkleRoot", config.getMerkleRoot());
        result.put("totalAirdrop", totalAirdrop);
        result.put("perAddress", record != null ? record.getAmount() : 0);

        if (record != null) {
            result.put("amount", record.getAmount());
            result.put("hasClaimed", record.getHasClaimed());
            result.put("claimTxHash", record.getClaimTxHash());
            result.put("canClaim", !record.getHasClaimed());


            log.info("User {} found in whitelist, canClaim: {}", address, !record.getHasClaimed());
        } else {
            result.put("amount", 0);
            result.put("hasClaimed", true);
            result.put("canClaim", false);
            log.warn("User {} not in whitelist", address);
        }
        return result;
    }

    @Override
    public Boolean hasClaimed(String address) {
        LambdaQueryWrapper<AirdropWhitelistEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AirdropWhitelistEntity::getAddress, address)
                .eq(AirdropWhitelistEntity::getHasClaimed, true);
        return whitelistMapper.selectCount(wrapper) > 0;
    }

    @Override
    public String claimOnChain(String address, BigInteger amount, List<String> merkleProof) {
        RLock lock = redissonClient.getLock(AIRDROP_LOCK_KEY_PREFIX + address);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new RuntimeException("System busy, try again");
            }

            LambdaQueryWrapper<AirdropWhitelistEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AirdropWhitelistEntity::getAddress, address);
            AirdropWhitelistEntity record = whitelistMapper.selectOne(wrapper);

            if (record == null) {
                throw new RuntimeException("User not in whitelist");
            }

            if (record.getHasClaimed()) {
                throw new RuntimeException("Already claimed");
            }

            log.info("Airdrop validation passed for: {}", address);
            return "VALIDATED";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}