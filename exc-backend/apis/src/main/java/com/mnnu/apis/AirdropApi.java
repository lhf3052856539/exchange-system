package com.mnnu.apis;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.AirdropClaimRequest;
import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import com.mnnu.vo.JsonVO;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;

/**
 * 空投 API 接口
 */

public interface AirdropApi {


    JsonVO<AirdropDTO> claimAirdrop(@CurrentUser String address,
                                    @RequestBody AirdropClaimRequest request);


    JsonVO<Boolean> hasClaimed(@RequestParam String address);


    JsonVO<AirdropInfoDTO> getAirdropInfo(@RequestParam String address);
}

