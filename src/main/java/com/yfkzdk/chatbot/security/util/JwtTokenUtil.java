package com.yfkzdk.chatbot.security.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 — jjwt 0.12.x API
 */
@Component
public class JwtTokenUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);
    private static final String CLAIM_USERNAME = "sub";
    private static final String CLAIM_CREATED = "created";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT token
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USERNAME, userDetails.getUsername());
        claims.put(CLAIM_CREATED, new Date());
        return Jwts.builder()
                .claims(claims)
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getKey())
                .compact();
    }

    /**
     * 从 token 中取用户名
     */
    public String getUserNameFromToken(String token) {
        try {
            return getClaimsFromToken(token).getSubject();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验 token 是否有效
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = getUserNameFromToken(token);
        return username != null
                && username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    /**
     * token 是否已过期
     */
    private boolean isTokenExpired(String token) {
        try {
            return getClaimsFromToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 刷新 token — 过期 30 分钟内不重复刷新
     */
    public String refreshToken(String oldToken) {
        Claims claims;
        try {
            claims = getClaimsFromToken(oldToken);
        } catch (Exception e) {
            return null;
        }
        if (claims == null || isTokenExpired(oldToken)) return null;

        Date created = new Date(claims.get(CLAIM_CREATED, Long.class));
        // 30 分钟内刷新过，返回原 token
        if (new Date().before(new Date(created.getTime() + 30 * 60 * 1000))) {
            return oldToken;
        }
        claims.put(CLAIM_CREATED, new Date());
        return Jwts.builder()
                .claims(claims)
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getKey())
                .compact();
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
