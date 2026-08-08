package com.yfkzdk.chatbot.config;

import com.yfkzdk.chatbot.common.config.BaseRedisConfig;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 配置 — 继承 BaseRedisConfig，激活 Spring Cache
 */
@Configuration
@EnableCaching
public class RedisConfig extends BaseRedisConfig {
}
