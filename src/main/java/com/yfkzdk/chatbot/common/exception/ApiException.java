package com.yfkzdk.chatbot.common.exception;

import com.yfkzdk.chatbot.common.api.IErrorCode;

/**
 * 自定义业务异常
 */
public class ApiException extends RuntimeException {
    private final IErrorCode errorCode;

    public ApiException(String message) {
        super(message);
        this.errorCode = null;
    }

    public ApiException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public IErrorCode getErrorCode() { return errorCode; }
}
