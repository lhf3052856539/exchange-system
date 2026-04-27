package com.mnnu.service;


import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 空投服务接口
 */



public interface AirdropService {


    Map<String, Object> getUserClaimInfo(String address);

    Boolean hasClaimed(String address);

    @Transactional
    String claimOnChain(String address, BigInteger amount, List<String> merkleProof);
}
