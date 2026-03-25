package com.mnnu.exception;


import com.mnnu.vo.JsonVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestController
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public JsonVO<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return JsonVO.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public JsonVO<Void> handleValidationException(ValidationException e) {
        log.warn("Validation exception: {}", e.getMessage());
        return JsonVO.error(400, e.getMessage());
    }

    /**
     * 处理方法参数无效异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public JsonVO<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Method argument not valid: {}", message);
        return JsonVO.error(400, message);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public JsonVO<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Bind exception: {}", message);
        return JsonVO.error(400, message);
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public JsonVO<Void> handleException(Exception e) {
        log.error("System error", e);
        return JsonVO.error(500, "系统繁忙，请稍后重试");
    }
}
