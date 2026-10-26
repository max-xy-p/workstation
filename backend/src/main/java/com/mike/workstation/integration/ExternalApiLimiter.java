package com.mike.workstation.integration;

import com.mike.workstation.config.ExternalApiLimitProperties;
import com.mike.workstation.exception.ExternalApiDeferredException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** Redis 只协调额度，不保存明文凭证或任务；协调不可用时延期而非绕过限流。 */
@Component
public class ExternalApiLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("redis/external-api-limit.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final ExternalApiLimitProperties properties;

    public ExternalApiLimiter(StringRedisTemplate redis, ExternalApiLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void acquire(String scope) {
        long wait = execute(scope, 0);
        if (wait > 0) {
            throw new ExternalApiDeferredException(Duration.ofMillis(wait), "平台调用额度不足或正在冷却，请稍后重试");
        }
    }

    public Duration cooldown(String scope, Duration delay) {
        try {
            return Duration.ofMillis(execute(scope, delay.toMillis()));
        } catch (ExternalApiDeferredException ex) {
            throw new ExternalApiDeferredException(
                    delay.compareTo(ex.delay()) > 0 ? delay : ex.delay(), ex.getMessage());
        }
    }

    private long execute(String scope, long cooldownMillis) {
        try {
            Long result =
                    redis.execute(
                            SCRIPT,
                            List.of(scope),
                            Integer.toString(properties.capacity()),
                            Integer.toString(properties.refillPerSecond()),
                            Long.toString(cooldownMillis));
            if (result == null || result < 0) {
                throw new IllegalStateException("限流协调结果不可用");
            }
            return result;
        } catch (RuntimeException ex) {
            // 不附带 Redis 异常，避免连接凭证或请求内容进入上层错误和日志。
            throw new ExternalApiDeferredException(
                    Duration.ofSeconds(properties.redisFailureDelaySeconds()), "限流协调暂时不可用，请稍后重试");
        }
    }

    static String scope(String provider, String normalizedBase, String token) {
        try {
            String value = provider + "\n" + normalizedBase + "\n" + token;
            return "workstation:external-api:v1:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境缺少 SHA-256", ex);
        }
    }
}
