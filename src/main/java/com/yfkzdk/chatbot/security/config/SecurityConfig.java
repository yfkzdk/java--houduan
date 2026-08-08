package com.yfkzdk.chatbot.security.config;

import com.yfkzdk.chatbot.security.component.JwtAuthenticationTokenFilter;
import com.yfkzdk.chatbot.security.component.RestAuthenticationEntryPoint;
import com.yfkzdk.chatbot.security.component.RestfulAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 核心配置 — Spring Boot 3.x / Security 6.x
 */
@Configuration
public class SecurityConfig {

    private final IgnoreUrlsConfig ignoreUrlsConfig;
    private final RestfulAccessDeniedHandler accessDeniedHandler;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAuthenticationTokenFilter jwtFilter;

    public SecurityConfig(IgnoreUrlsConfig ignoreUrlsConfig,
                          RestfulAccessDeniedHandler accessDeniedHandler,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAuthenticationTokenFilter jwtFilter) {
        this.ignoreUrlsConfig = ignoreUrlsConfig;
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 白名单 URL 放行
        var registry = http.authorizeHttpRequests(auth -> {
            for (String url : ignoreUrlsConfig.getUrls()) {
                auth.requestMatchers(url).permitAll();
            }
            auth.requestMatchers(HttpMethod.OPTIONS).permitAll();
            auth.anyRequest().authenticated();
        });

        registry
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
