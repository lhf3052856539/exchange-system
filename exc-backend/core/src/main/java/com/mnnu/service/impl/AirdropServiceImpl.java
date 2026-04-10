package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mnnu.entity.AirdropConfigEntity;
import com.mnnu.entity.AirdropWhitelistEntity;
import com.mnnu.mapper.AirdropConfigMapper;
import com.mnnu.mapper.AirdropWhitelistMapper;
import com.mnnu.service.AirdropService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class AirdropServiceImpl implements AirdropService {

    @Autowired
    private AirdropConfigMapper configMapper;

    @Autowired
    private AirdropWhitelistMapper whitelistMapper;

    /**
     * 查询用户在当前活跃空投中的权益
     */
    @Override
    public Map<String, Object> getUserClaimInfo(String address) {
        // 查找当前正在进行的活动
        LambdaQueryWrapper<AirdropConfigEntity> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(AirdropConfigEntity::getStatus, 1) // 进行中
                .orderByDesc(AirdropConfigEntity::getCreateTime)
                .last("LIMIT 1");
        AirdropConfigEntity config = configMapper.selectOne(configWrapper);

        if (config == null) {
            return Collections.emptyMap();
        }

        // 查找该用户在白名单中的记录
        LambdaQueryWrapper<AirdropWhitelistEntity> whiteWrapper = new LambdaQueryWrapper<>();
        whiteWrapper.eq(AirdropWhitelistEntity::getConfigId, config.getId())
                .eq(AirdropWhitelistEntity::getAddress, address);
        AirdropWhitelistEntity record = whitelistMapper.selectOne(whiteWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("isActive", true);
        result.put("merkleRoot", config.getMerkleRoot());

        if (record != null) {
            result.put("amount", record.getAmount());
            result.put("hasClaimed", record.getHasClaimed());
            result.put("claimTxHash", record.getClaimTxHash());
        } else {
            result.put("amount", 0);
            result.put("hasClaimed", true); // 不在名单视为无资格
        }
        return result;
    }
}