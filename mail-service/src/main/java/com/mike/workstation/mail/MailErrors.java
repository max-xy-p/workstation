package com.mike.workstation.mail;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class MailErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> expected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception error) {
        // 不返回异常详情；绑定错误可能含验证码、邮件正文或服务凭据。
        HttpStatus status = error instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || error instanceof org.springframework.http.converter.HttpMessageNotReadableException
                ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of("message",
                status == HttpStatus.BAD_REQUEST ? "邮件请求格式无效" : "邮件服务暂时不可用"));
    }
}
