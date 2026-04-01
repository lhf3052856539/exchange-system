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
import org.springframework.web.bind.annotation.*;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/apis/airdrop")
@RequiredArgsConstructor
public class AirdropController implements AirdropApi {

    private final AirdropService airdropService;

    @PostMapping("/claim")
    public JsonVO<AirdropDTO> claimAirdrop(@CurrentUser String address,
                                           @RequestBody AirdropClaimRequest request) {
        // 验证参数
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        // Merkle proof 可以为空（当只有一个叶子时）
        List<String> merkleProof = request.getMerkleProof();
        if (merkleProof == null) {
            throw new IllegalArgumentException("Merkle proof is required");
        }

        // 添加调试日志
        log.info("🔍 Received airdrop claim request:");
        log.info("   Address: {}", address);
        log.info("   Amount: {}", request.getAmount());
        log.info("   Merkle Proof (hex strings): {}", merkleProof);
        log.info("   Proof length: {}", merkleProof.size());

        // 将十六进制字符串数组转换为 byte[] 数组（过滤掉 null 和空值）
        List<byte[]> proofBytes = merkleProof.stream()
                .filter(hex -> hex != null && !hex.isEmpty())
                .map(this::hexStringToByteArray)
                .toList();

        // 添加调试日志
        log.info("   Converted proof bytes count: {}", proofBytes.size());
        for (int i = 0; i < proofBytes.size(); i++) {
            log.info("   Proof[{}] length: {} bytes", i, proofBytes.get(i).length);
        }

        return JsonVO.success(airdropService.claimAirdrop(address, request.getAmount(), proofBytes));
    }

    @GetMapping("/has-claimed")
    public JsonVO<Boolean> hasClaimed(@CurrentUser String address) {
        return JsonVO.success(airdropService.hasClaimed(address));
    }

    @GetMapping("/info")
    public JsonVO<AirdropInfoDTO> getAirdropInfo(@CurrentUser String address) {
        return JsonVO.success(airdropService.getAirdropInfo(address));
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexStringToByteArray(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }

        hex = hex.trim();
        if (hex.isEmpty()) {
            throw new IllegalArgumentException("Hex string cannot be empty");
        }

        if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }

        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length: " + hex);
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
