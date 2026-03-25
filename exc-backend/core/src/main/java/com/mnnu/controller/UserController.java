package com.mnnu.controller;

import com.mnnu.apis.UserApi;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.UserDTO;
import com.mnnu.dto.UserTradeStatsDTO;
import com.mnnu.service.UserService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/apis/user")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public JsonVO<String> login(@RequestParam String address, @RequestParam String signature) {
        try {
            String token = userService.login(address, signature);
            return JsonVO.success(token);
        } catch (Exception e) {
            log.error("Login failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<UserDTO> register(@RequestParam String address) {
        try {
            UserDTO user = userService.register(address);
            return JsonVO.success(user);
        } catch (Exception e) {
            log.error("Register failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<UserDTO> getUserInfo(@CurrentUser String address) {
        try {
            UserDTO user = userService.getUserInfo(address);
            return JsonVO.success(user);
        } catch (Exception e) {
            log.error("Get user info failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<UserTradeStatsDTO> getTradeStats(@CurrentUser String address) {
        try {
            UserTradeStatsDTO stats = userService.getUserTradeStats(address);
            return JsonVO.success(stats);
        } catch (Exception e) {
            log.error("Get trade stats failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<UserDTO> updateUserType(@PathVariable String address) {
        try {
            UserDTO user = userService.updateUserType(address);
            return JsonVO.success(user);
        } catch (Exception e) {
            log.error("Update user type failed", e);
            return JsonVO.error(e.getMessage());
        }
    }
}
