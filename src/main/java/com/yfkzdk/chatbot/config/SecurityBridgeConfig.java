package com.yfkzdk.chatbot.config;

import com.yfkzdk.chatbot.security.component.JwtAuthenticationTokenFilter;
import com.yfkzdk.chatbot.security.util.JwtTokenUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 桥接配置 — 将 JWT Filter 注入 UserDetailsService 依赖
 * <p>
 * 等 auth 模块写好 UserService 后，改这里的 userDetailsService() 返回值即可。
 * 目前返回一个临时空实现，让项目能启动。
 */
@Configuration
public class SecurityBridgeConfig {

    private final JwtAuthenticationTokenFilter jwtFilter;
    private final JwtTokenUtil jwtTokenUtil;

    public SecurityBridgeConfig(JwtAuthenticationTokenFilter jwtFilter, JwtTokenUtil jwtTokenUtil) {
        this.jwtFilter = jwtFilter;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // TODO: Phase 2 — 换成真正的 UserService
        return username -> {
            throw new RuntimeException("User service not yet implemented — coming in Phase 2");
        };
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
