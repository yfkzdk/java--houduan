package com.yfkzdk.chatbot.config;

import com.yfkzdk.chatbot.modules.user.service.UserService;
import com.yfkzdk.chatbot.security.component.JwtAuthenticationTokenFilter;
import com.yfkzdk.chatbot.security.util.JwtTokenUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 桥接配置 — 将 JWT Filter 注入真实的 UserService（UserDetailsService）
 */
@Configuration
public class SecurityBridgeConfig {

    private final JwtAuthenticationTokenFilter jwtFilter;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;

    public SecurityBridgeConfig(JwtAuthenticationTokenFilter jwtFilter,
                                 JwtTokenUtil jwtTokenUtil,
                                 UserService userService) {
        this.jwtFilter = jwtFilter;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userService = userService;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userService.loadUserByUsername(username);
    }

    /**
     * 手动补齐 JwtAuthenticationTokenFilter 的 setter 依赖
     * （因为 Filter 不是 Spring Bean，不能走构造注入）
     */
    @Bean
    public Object jwtFilterInitializer(UserDetailsService userDetailsService) {
        jwtFilter.setUserDetailsService(userDetailsService);
        jwtFilter.setJwtTokenUtil(jwtTokenUtil);
        return new Object(); // just a trigger
    }
}
