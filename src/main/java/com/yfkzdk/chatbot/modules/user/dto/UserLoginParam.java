package com.yfkzdk.chatbot.modules.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 用户登录参数
 */
@Data
public class UserLoginParam {

    @NotEmpty(message = "用户名不能为空")
    private String username;

    @NotEmpty(message = "密码不能为空")
    private String password;
}
