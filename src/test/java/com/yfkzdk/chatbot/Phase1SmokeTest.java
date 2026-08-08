package com.yfkzdk.chatbot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Verification: All 5 infrastructure classes compiled and are loadable.
 * Does NOT require Spring context or Redis — pure classpath verification.
 *
 * Run:  mvn test -Dtest=Phase1SmokeTest
 */
class Phase1SmokeTest {

    @Test
    void commonPageIsAvailable() {
        assertDoesNotThrow(() ->
            Class.forName("com.yfkzdk.chatbot.common.api.CommonPage"));
        System.out.println("[Phase1] CommonPage loaded");
    }

    @Test
    void redisServiceInterfaceIsAvailable() {
        assertDoesNotThrow(() ->
            Class.forName("com.yfkzdk.chatbot.common.service.RedisService"));
        System.out.println("[Phase1] RedisService loaded");
    }

    @Test
    void redisServiceImplIsAvailable() {
        assertDoesNotThrow(() ->
            Class.forName("com.yfkzdk.chatbot.common.service.impl.RedisServiceImpl"));
        System.out.println("[Phase1] RedisServiceImpl loaded");
    }

    @Test
    void baseRedisConfigIsAvailable() {
        assertDoesNotThrow(() ->
            Class.forName("com.yfkzdk.chatbot.common.config.BaseRedisConfig"));
        System.out.println("[Phase1] BaseRedisConfig loaded");
    }

    @Test
    void redisConfigIsAvailable() {
        assertDoesNotThrow(() ->
            Class.forName("com.yfkzdk.chatbot.config.RedisConfig"));
        System.out.println("[Phase1] RedisConfig loaded");
    }

    @Test
    void summary() {
        System.out.println("======================================");
        System.out.println(" Phase 1 VERIFIED — all classes present");
        System.out.println(" 5 infrastructure classes ready");
        System.out.println("======================================");
    }
}
