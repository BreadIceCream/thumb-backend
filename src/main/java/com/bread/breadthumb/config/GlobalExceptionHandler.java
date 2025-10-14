package com.bread.breadthumb.config;

import com.bread.breadthumb.common.Result;
import com.bread.breadthumb.exception.BusinessException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author huang
 */
@RestControllerAdvice(basePackages = "com.bread.breadthumb.controller")
@Hidden
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.error("BusinessExceptionHandler: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("GlobalExceptionHandler: {}", e.getMessage());
        return Result.error(500, e.getMessage());
    }
}
