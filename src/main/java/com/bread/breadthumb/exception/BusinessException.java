package com.bread.breadthumb.exception;

import lombok.Getter;

/**
 * @author huang
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

}
