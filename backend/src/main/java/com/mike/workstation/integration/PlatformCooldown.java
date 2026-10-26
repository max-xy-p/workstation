package com.mike.workstation.integration;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** 解释平台限流响应；没有可靠提示时采用部署配置的保守冷却。 */
final class PlatformCooldown {
    private PlatformCooldown() {}

    static boolean limited(int status, HttpHeaders headers) {
        return status == 429
                || (status == 403
                        && (headers.firstValue("Retry-After").isPresent()
                                || headers.firstValue("X-RateLimit-Remaining")
                                        .orElse("1")
                                        .equals("0")));
    }

    static boolean secondaryLimit(int status, String provider, String body) {
        if (status != 403 || !"GITHUB".equals(provider) || body == null) {
            return false;
        }
        String message = body.toLowerCase(Locale.ROOT);
        return message.contains("secondary rate limit")
                || message.contains("api rate limit exceeded");
    }

    static Duration delay(HttpHeaders headers, Clock clock, int fallbackSeconds) {
        Instant now = clock.instant();
        long retry = retryMillis(headers.firstValue("Retry-After").orElse(""), now);
        long reset = 0;
        if (headers.firstValue("X-RateLimit-Remaining").orElse("1").equals("0")) {
            reset =
                    resetMillis(
                            headers.firstValue("X-RateLimit-Reset")
                                    .orElse(headers.firstValue("RateLimit-Reset").orElse("")),
                            now);
        }
        long millis = Math.max(retry, reset);
        // 避免溢出或异常头造成无限延期；超过七天的提示视为无效。
        return Duration.ofMillis(
                millis > 0 && millis <= Duration.ofDays(7).toMillis()
                        ? millis + 1000
                        : fallbackSeconds * 1000L);
    }

    private static long retryMillis(String value, Instant now) {
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 && seconds <= 604800 ? seconds * 1000 : 0;
        } catch (NumberFormatException ex) {
            try {
                return Duration.between(
                                now,
                                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                        .toInstant())
                        .toMillis();
            } catch (DateTimeParseException ignored) {
                return 0;
            }
        }
    }

    private static long resetMillis(String value, Instant now) {
        try {
            long seconds = Long.parseLong(value);
            if (seconds <= now.getEpochSecond() || seconds - now.getEpochSecond() > 604800) {
                return 0;
            }
            return Duration.between(now, Instant.ofEpochSecond(seconds)).toMillis();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
