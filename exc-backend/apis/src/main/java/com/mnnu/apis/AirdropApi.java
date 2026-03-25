package com.mnnu.apis;
import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import com.mnnu.vo.JsonVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigInteger;
import java.util.List;

/**
 * 空投 API 接口
 */

public interface AirdropApi {


    JsonVO<AirdropDTO> claimAirdrop(@RequestParam String address, BigInteger amount, List<byte[]> merkleProof);


    JsonVO<Boolean> hasClaimed(@RequestParam String address);


    JsonVO<AirdropInfoDTO> getAirdropInfo(@RequestParam String address);
}

