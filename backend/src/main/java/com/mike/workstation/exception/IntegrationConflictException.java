package com.mike.workstation.exception;

/** 配置版本或幂等请求发生冲突。 */
public class IntegrationConflictException extends RuntimeException {
    public IntegrationConflictException(String message) {
        super(message);
    }
}
