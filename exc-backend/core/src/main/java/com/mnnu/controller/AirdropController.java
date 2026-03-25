package com.mnnu.controller;


import com.mnnu.apis.AirdropApi;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import com.mnnu.service.AirdropService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;

/**
 * 空投控制器
 */

@RestController
@RequestMapping("/apis/airdrop")
@RequiredArgsConstructor
public class AirdropController implements AirdropApi {

    private final AirdropService airdropService;


    @PostMapping("/claim")
    public JsonVO<AirdropDTO> claimAirdrop(@CurrentUser String address,BigInteger amount, List<byte[]> merkleProof) {
        return JsonVO.success(airdropService.claimAirdrop(address, amount,merkleProof));
    }


    @GetMapping("/has-claimed")
    public JsonVO<Boolean> hasClaimed(@CurrentUser String address) {
        return JsonVO.success(airdropService.hasClaimed(address));
    }


    @GetMapping("/info")
    public JsonVO<AirdropInfoDTO> getAirdropInfo(@CurrentUser String address) {
        return JsonVO.success(airdropService.getAirdropInfo(address));
    }
}
