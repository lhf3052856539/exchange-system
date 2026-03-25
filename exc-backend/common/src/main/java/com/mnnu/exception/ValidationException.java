package com.mnnu.exception;

/**
 * 参数验证异常
 */


public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(400, message);
    }
}
