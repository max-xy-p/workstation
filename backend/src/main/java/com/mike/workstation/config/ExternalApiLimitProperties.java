package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 所有执行器必须使用相同的出站限流配置，额度按平台实例与凭证共享。 */
@ConfigurationProperties(prefix = "workstation.integrations.rate-limit")
public record ExternalApiLimitProperties(
        int capacity,
        int refillPerSecond,
        int fallbackCooldownSeconds,
        int redisFailureDelaySeconds) {
    public ExternalApiLimitProperties {
        if (capacity < 1
                || capacity > 10000
                || refillPerSecond < 1
                || refillPerSecond > 1000
                || fallbackCooldownSeconds < 1
                || fallbackCooldownSeconds > 86400
                || redisFailureDelaySeconds < 1
                || redisFailureDelaySeconds > 3600) {
            throw new IllegalArgumentException("外部 API 限流配置不合法");
        }
    }
}
