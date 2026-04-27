package com.mnnu.controller;

import com.mnnu.config.CurrentUser;
import com.mnnu.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 区块链授权管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/apis/blockchain")
public class BlockchainController {
    @Autowired
    private BlockchainService blockchainService;

    /**
     * 检查用户对 Exchange 合约的 EXTH 授权额度
     */
    @GetMapping("/allowance")
    public ResponseEntity<Map<String, Object>> checkAllowance(@CurrentUser String address) {
        try {
            String exchangeContractAddress = blockchainService.getExchangeContractAddress();
            BigInteger allowance = blockchainService.checkExthAllowance(address, exchangeContractAddress);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("allowance", allowance.toString());
            result.put("spender", exchangeContractAddress);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Check allowance failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 授权 Exchange 合约使用 EXTH 代币
     */
    @PostMapping("/approve")
    public ResponseEntity<Map<String, Object>> approve(@CurrentUser String address,
                                                       @RequestBody Map<String, String> params) {
        try {
            String amountStr = params.get("amount");
            if (amountStr == null || amountStr.isEmpty()) {
                throw new IllegalArgumentException("Amount is required");
            }

            BigInteger amount = new BigInteger(amountStr);
            String exchangeContractAddress = blockchainService.getExchangeContractAddress();

            String txHash = blockchainService.approveExth(exchangeContractAddress, amount);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("txHash", txHash);
            result.put("spender", exchangeContractAddress);
            result.put("amount", amount.toString());

            log.info("EXTH approval granted: user={}, spender={}, amount={}, txHash={}",
                    address, exchangeContractAddress, amount, txHash);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Approve failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
