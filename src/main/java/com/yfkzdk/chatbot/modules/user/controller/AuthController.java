package com.yfkzdk.chatbot.modules.user.controller;

import com.yfkzdk.chatbot.common.api.CommonResult;
import com.yfkzdk.chatbot.modules.user.dto.UserLoginParam;
import com.yfkzdk.chatbot.modules.user.dto.UserRegisterParam;
import com.yfkzdk.chatbot.modules.user.model.User;
import com.yfkzdk.chatbot.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器 — 注册 / 登录 / 刷新 Token
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "AuthController", description = "用户认证")
public class AuthController {

    @Value("${jwt.token-header:Authorization}")
    private String tokenHeader;

    @Value("${jwt.token-head:Bearer }")
    private String tokenHead;

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public CommonResult<User> register(@Validated @RequestBody UserRegisterParam param) {
        User user = userService.register(param);
        if (user == null) {
            return CommonResult.failed("用户名已存在");
        }
        return CommonResult.success(user);
    }

    @Operation(summary = "用户登录，返回 JWT Token")
    @PostMapping("/login")
    public CommonResult<Map<String, String>> login(@Validated @RequestBody UserLoginParam param) {
        String token = userService.login(param);
        if (token == null) {
            return CommonResult.validateFailed("用户名或密码错误");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "刷新 Token")
    @GetMapping("/refresh")
    public CommonResult<Map<String, String>> refreshToken(HttpServletRequest request) {
        String token = request.getHeader(tokenHeader);
        if (token != null && token.startsWith(tokenHead)) {
            token = token.substring(tokenHead.length());
        }
        String refreshed = userService.refreshToken(token);
        if (refreshed == null) {
            return CommonResult.failed("Token 已过期，请重新登录");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", refreshed);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }
}
