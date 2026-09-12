package com.mike.workstation.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
/**
 * 全局异常处理器。
 *
 * <p>把 Service 或框架抛出的异常转换为前端易处理的 JSON 错误响应。</p>
 */
public class GlobalExceptionHandler {
    /**
     * 处理资源不存在错误。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", ex.getMessage()));
    }

    /**
     * 处理业务规则错误，例如删除受限、必填字段缺失等。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("BUSINESS_ERROR", ex.getMessage()));
    }

    /**
     * 处理请求体格式错误或枚举值无法解析等参数问题。
     */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("BAD_REQUEST", "请求参数不合法"));
    }

    /**
     * 处理数据库唯一键、外键等约束错误。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("DATA_INTEGRITY_ERROR", "数据约束校验失败"));
    }
}
