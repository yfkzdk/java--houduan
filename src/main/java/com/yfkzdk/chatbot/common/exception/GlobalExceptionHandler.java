package com.yfkzdk.chatbot.common.exception;

import com.yfkzdk.chatbot.common.api.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseBody
    @ExceptionHandler(value = ApiException.class)
    public CommonResult<?> handle(ApiException e) {
        if (e.getErrorCode() != null) {
            return CommonResult.failed(e.getErrorCode());
        }
        return CommonResult.failed(e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public CommonResult<?> handleValid(MethodArgumentNotValidException e) {
        var field = e.getBindingResult().getFieldError();
        String msg = field != null ? field.getField() + field.getDefaultMessage() : "参数校验失败";
        return CommonResult.validateFailed(msg);
    }

    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public CommonResult<?> handleAll(Exception e) {
        log.error("Unhandled exception", e);
        return CommonResult.failed("服务器内部错误");
    }
}
