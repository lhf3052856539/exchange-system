package com.mnnu.apis;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.AirdropClaimRequest;
import com.mnnu.dto.AirdropDTO;
import com.mnnu.dto.AirdropInfoDTO;
import com.mnnu.vo.JsonVO;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 空投 API 接口
 */

public interface AirdropApi {
    JsonVO<Map<String, Object>> getClaimInfo(@CurrentUser String address);



}

