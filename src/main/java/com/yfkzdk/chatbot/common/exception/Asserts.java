package com.yfkzdk.chatbot.common.exception;

/**
 * 断言工具 — 条件不满足时抛 ApiException
 */
public class Asserts {

    public static void fail(String message) {
        throw new ApiException(message);
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) throw new ApiException(message);
    }
}
