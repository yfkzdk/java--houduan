package com.yfkzdk.chatbot.security.component;

import cn.hutool.core.util.StrUtil;
import com.yfkzdk.chatbot.security.util.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器 — 从 Authorization: Bearer xxx 解析用户身份
 */
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Value("${jwt.token-header}")
    private String tokenHeader;

    @Value("${jwt.token-head}")
    private String tokenHead;

    private UserDetailsService userDetailsService;
    private JwtTokenUtil jwtTokenUtil;

    // Setter injection — Bean 由 CommonSecurityConfig 创建后手动注入
    public void setUserDetailsService(UserDetailsService svc) { this.userDetailsService = svc; }
    public void setJwtTokenUtil(JwtTokenUtil util) { this.jwtTokenUtil = util; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(this.tokenHeader);
        if (header == null || !header.startsWith(this.tokenHead)) {
            chain.doFilter(request, response);
            return;
        }

        String token = StrUtil.removePrefix(header, this.tokenHead);
        String username = jwtTokenUtil.getUserNameFromToken(token);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtTokenUtil.validateToken(token, userDetails)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT authenticated: {}", username);
            }
        }
        chain.doFilter(request, response);
    }
}
