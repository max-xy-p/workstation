package com.mike.workstation.exception;

import java.time.OffsetDateTime;

public record ApiError(String code, String message, OffsetDateTime timestamp) {
    /**
     * 创建统一错误响应对象。
     *
     * @param code 面向前端判断的错误码
     * @param message 面向用户展示的错误说明
     * @return 错误响应
     */
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, OffsetDateTime.now());
    }
}
