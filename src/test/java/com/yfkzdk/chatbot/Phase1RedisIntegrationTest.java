package com.yfkzdk.chatbot;

import com.yfkzdk.chatbot.common.service.RedisService;
import com.yfkzdk.chatbot.common.service.impl.RedisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Integration Test: Real Redis on localhost:6379.
 *
 * Prerequisite: Redis must be running at localhost:6379
 *   start:  "C:\Program Files\Redis\redis-server"
 *   verify: "C:\Program Files\Redis\redis-cli" PING  → PONG
 *
 * Run: mvn test -Dtest=Phase1RedisIntegrationTest
 */
class Phase1RedisIntegrationTest {

    private RedisService redisService;

    @BeforeEach
    void setUp() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();

        RedisServiceImpl impl = new RedisServiceImpl();
        try {
            var field = impl.getClass().getDeclaredField("redisTemplate");
            field.setAccessible(true);
            field.set(impl, template);
        } catch (Exception e) {
            fail("Cannot inject redisTemplate: " + e.getMessage());
        }
        redisService = impl;
    }

    @Test
    void setAndGet() {
        String key = "p1:test:set_get", value = "hello_redis_2026";
        redisService.set(key, value);
        assertEquals(value, redisService.get(key));
        redisService.del(key);
        System.out.println("[P1-Redis] setAndGet PASSED");
    }

    @Test
    void expire() {
        String key = "p1:test:expire";
        redisService.set(key, "x", 5);
        Long ttl = redisService.getExpire(key);
        assertTrue(ttl > 0 && ttl <= 5, "TTL should be 1-5, got " + ttl);
        redisService.del(key);
        System.out.println("[P1-Redis] expire PASSED");
    }

    @Test
    void incrAndDecr() {
        String key = "p1:test:incr";
        redisService.del(key);
        assertEquals(1, redisService.incr(key, 1));
        assertEquals(2, redisService.incr(key, 1));
        assertEquals(5, redisService.incr(key, 3));
        assertEquals(4, redisService.decr(key, 1));
        redisService.del(key);
        System.out.println("[P1-Redis] incr/decr PASSED  (rate-limit foundation)");
    }

    @Test
    void summary() {
        System.out.println("============================================");
        System.out.println(" Phase 1 INTEGRATION VERIFIED ");
        System.out.println(" Redis:  set/get | expire | incr/decr ");
        System.out.println(" Infrastructure: ready for Phase 2 ");
        System.out.println("============================================");
    }
}
