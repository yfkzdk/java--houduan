package com.yfkzdk.chatbot.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yfkzdk.chatbot.modules.user.dto.UserLoginParam;
import com.yfkzdk.chatbot.modules.user.dto.UserRegisterParam;
import com.yfkzdk.chatbot.modules.user.model.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 用户业务接口
 */
public interface UserService extends IService<User> {

    /** 注册，重名返回 null */
    User register(UserRegisterParam param);

    /** 登录，返回 JWT token，失败返回 null */
    String login(UserLoginParam param);

    /** 刷新 token */
    String refreshToken(String oldToken);

    /** 按用户名查用户 */
    User getByUsername(String username);

    /** Spring Security UserDetailsService 回调 */
    UserDetails loadUserByUsername(String username);
}
