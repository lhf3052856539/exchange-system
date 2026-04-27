package com.mnnu.controller;

import com.mnnu.apis.AirdropApi;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.AirdropClaimRequest;
import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import com.mnnu.service.AirdropService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/apis/airdrop")
public class AirdropController implements AirdropApi {
    @Autowired
    private AirdropService airdropService;

    /**
     * 查询当前用户的空投领取资格及 Merkle Proof
     * 前端获取数据后，自行调用链上合约进行领取
     */
    @GetMapping("/claim-info")
    public JsonVO<Map<String, Object>> getClaimInfo(@CurrentUser String address) {
        log.info("🔍 Querying airdrop claim info for: {}", address);
            Map<String, Object> info = airdropService.getUserClaimInfo(address);

            if (info.isEmpty()) {
                return JsonVO.success(Map.of("isActive", false));
            }

            return JsonVO.success(info);

    }

    /**
     * 领取空投（先查数据库验证，前端再调用链上合约）
     */
    @PostMapping("/claim")
    public JsonVO<Map<String, Object>> claimAirdrop(@CurrentUser String address) {
        log.info("Validating airdrop claim for: {}", address);

        try {
            Map<String, Object> claimInfo = airdropService.getUserClaimInfo(address);

            if (claimInfo.isEmpty() || !(Boolean) claimInfo.getOrDefault("canClaim", false)) {
                return JsonVO.error("No airdrop eligibility or already claimed");
            }

            BigInteger amount = new BigInteger(claimInfo.get("amount").toString());
            @SuppressWarnings("unchecked")
            List<String> proof = (List<String>) claimInfo.get("merkleProof");

            airdropService.claimOnChain(address, amount, proof);

            Map<String, Object> result = new HashMap<>();
            result.put("validated", true);
            result.put("amount", amount.toString());
            result.put("merkleProof", proof);

            return JsonVO.success(result);

        } catch (Exception e) {
            log.error("Failed to validate airdrop claim", e);
            return JsonVO.error(e.getMessage());
        }
    }
}