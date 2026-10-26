package com.mike.workstation.exception;

import java.time.Duration;

/** 请求尚不可执行；后台持久化延期，交互式验证沿用业务错误响应。 */
public class ExternalApiDeferredException extends BusinessException {
    private final Duration delay;

    public ExternalApiDeferredException(Duration delay, String message) {
        super(message);
        this.delay = delay;
    }

    public Duration delay() {
        return delay;
    }
}
