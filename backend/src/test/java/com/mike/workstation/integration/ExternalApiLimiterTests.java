package com.mike.workstation.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.ExternalApiLimitProperties;
import com.mike.workstation.exception.ExternalApiDeferredException;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

class ExternalApiLimiterTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ExternalApiLimiter limiter =
            new ExternalApiLimiter(redis, new ExternalApiLimitProperties(5, 1, 60, 30));

    @Test
    void acquiresOrDefersFromAtomicDecision() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L, 1500L);
        limiter.acquire("scope");
        assertThatThrownBy(() -> limiter.acquire("scope"))
                .isInstanceOfSatisfying(
                        ExternalApiDeferredException.class,
                        ex -> assertThat(ex.delay()).isEqualTo(Duration.ofMillis(1500)));
    }

    @Test
    void redisFailureAndInvalidResponsesDeferWithoutLeakingCredentials() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("password-secret"));
        assertThatThrownBy(() -> limiter.acquire("scope"))
                .hasMessageNotContaining("password-secret")
                .isInstanceOfSatisfying(
                        ExternalApiDeferredException.class,
                        ex -> assertThat(ex.delay()).isEqualTo(Duration.ofSeconds(30)));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null, -1L);
        assertThatThrownBy(() -> limiter.acquire("scope"))
                .isInstanceOf(ExternalApiDeferredException.class);
        assertThatThrownBy(() -> limiter.acquire("scope"))
                .isInstanceOf(ExternalApiDeferredException.class);
    }

    @Test
    void cooldownUsesSharedLongestDelayAndRetainsPlatformWaitOnRedisFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(120000L);
        assertThat(limiter.cooldown("scope", Duration.ofSeconds(60)))
                .isEqualTo(Duration.ofMinutes(2));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("secret"));
        assertThatThrownBy(() -> limiter.cooldown("scope", Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(
                        ExternalApiDeferredException.class,
                        ex -> assertThat(ex.delay()).isEqualTo(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> limiter.cooldown("scope", Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(
                        ExternalApiDeferredException.class,
                        ex -> assertThat(ex.delay()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void scopeSharesCredentialAcrossWorkspacesAndSeparatesInstances() {
        String key = ExternalApiLimiter.scope("GITHUB", "https://github.com", "private-token");
        assertThat(key).startsWith("workstation:external-api:v1:").doesNotContain("private-token");
        assertThat(key)
                .isEqualTo(
                        ExternalApiLimiter.scope("GITHUB", "https://github.com", "private-token"));
        assertThat(key)
                .isNotEqualTo(
                        ExternalApiLimiter.scope("GITHUB", "https://github.com", "other-token"));
        assertThat(key)
                .isNotEqualTo(
                        ExternalApiLimiter.scope("GITLAB", "https://gitlab.com", "private-token"));
        assertThat(ExternalApiLimiter.scope("GITLAB", "https://a.test", "private-token"))
                .isNotEqualTo(
                        ExternalApiLimiter.scope("GITLAB", "https://b.test", "private-token"));
    }

    @Test
    void rejectsInvalidConfiguration() {
        for (int[] values :
                List.of(
                        new int[] {0, 1, 60, 30},
                        new int[] {10001, 1, 60, 30},
                        new int[] {5, 0, 60, 30},
                        new int[] {5, 1001, 60, 30},
                        new int[] {5, 1, 0, 30},
                        new int[] {5, 1, 86401, 30},
                        new int[] {5, 1, 60, 0},
                        new int[] {5, 1, 60, 3601})) {
            assertThatThrownBy(
                            () ->
                                    new ExternalApiLimitProperties(
                                            values[0], values[1], values[2], values[3]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
