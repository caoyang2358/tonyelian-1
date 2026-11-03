package com.coppertwin.backend.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        return ResponseEntity.status(ex.status()).body(ApiResponse.error(ex.code(), ex.getMessage(), null));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        var details=ex.getBindingResult().getFieldErrors().stream().map(e->Map.of("field",e.getField(),"message",String.valueOf(e.getDefaultMessage()))).toList();
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR","请求参数校验失败",details));
    }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> constraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR",ex.getMessage(),null));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiResponse<Void>> malformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR","请求参数格式错误",null));
    }
    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    ResponseEntity<ApiResponse<Void>> dependency() {
        return ResponseEntity.status(503).body(ApiResponse.error("DEPENDENCY_UNAVAILABLE","数据库不可用",null));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unknown(Exception ex) {
        log.error("Unhandled request failure",ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR","服务器内部错误",null));
    }
}

