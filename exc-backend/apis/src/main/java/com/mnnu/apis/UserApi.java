package com.mnnu.apis;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.UserDTO;
import com.mnnu.dto.UserTradeStatsDTO;
import com.mnnu.vo.JsonVO;

import org.springframework.web.bind.annotation.*;

/**
 * 用户 API 接口
 */

public interface UserApi {

    /**
     * 用户登录（返回 token）
     */
    @PostMapping("/login")
    JsonVO<String> login(
            @RequestParam String address,
            @RequestParam String signature
    );

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    JsonVO<UserDTO> getUserInfo(@CurrentUser String address);

    /**
     * 获取交易统计
     */
    @GetMapping("/trade-stats")
    JsonVO<UserTradeStatsDTO> getTradeStats(@CurrentUser String address);


}
