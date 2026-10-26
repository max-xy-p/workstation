package com.mike.workstation.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

class PlatformCooldownTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);

    private HttpHeaders headers(Map<String, List<String>> values) {
        return HttpHeaders.of(values, (a, b) -> true);
    }

    @Test
    void classifiesLimitsWithoutTreatingPermissionFailureAsQuota() {
        assertThat(PlatformCooldown.limited(429, headers(Map.of()))).isTrue();
        assertThat(PlatformCooldown.limited(403, headers(Map.of("Retry-After", List.of("5")))))
                .isTrue();
        assertThat(
                        PlatformCooldown.limited(
                                403, headers(Map.of("X-RateLimit-Remaining", List.of("0")))))
                .isTrue();
        assertThat(PlatformCooldown.limited(403, headers(Map.of()))).isFalse();
        assertThat(PlatformCooldown.limited(401, headers(Map.of()))).isFalse();
        assertThat(
                        PlatformCooldown.secondaryLimit(
                                403, "GITHUB", "You have exceeded a secondary rate limit"))
                .isTrue();
        assertThat(PlatformCooldown.secondaryLimit(403, "GITHUB", "API rate limit exceeded"))
                .isTrue();
        assertThat(PlatformCooldown.secondaryLimit(403, "GITHUB", "permission denied")).isFalse();
        assertThat(PlatformCooldown.secondaryLimit(403, "GITLAB", "secondary rate limit"))
                .isFalse();
        assertThat(PlatformCooldown.secondaryLimit(200, "GITHUB", "secondary rate limit"))
                .isFalse();
        assertThat(PlatformCooldown.secondaryLimit(403, "GITHUB", null)).isFalse();
    }

    @Test
    void honorsLongestRetryAfterAndEpochResetWithSafetyMargin() {
        var h =
                headers(
                        Map.of(
                                "Retry-After",
                                List.of("10"),
                                "X-RateLimit-Remaining",
                                List.of("0"),
                                "X-RateLimit-Reset",
                                List.of(Long.toString(clock.instant().getEpochSecond() + 100))));
        assertThat(PlatformCooldown.delay(h, clock, 60)).isEqualTo(Duration.ofSeconds(101));
        assertThat(
                        PlatformCooldown.delay(
                                headers(
                                        Map.of(
                                                "Retry-After",
                                                List.of("Fri, 23 Oct 2026 00:02:00 GMT"))),
                                clock,
                                60))
                .isEqualTo(Duration.ofSeconds(121));
    }

    @Test
    void malformedPastAndOverflowHeadersUseBoundedFallback() {
        for (String value :
                List.of(
                        "garbage",
                        "-1",
                        "0",
                        "999999999999999999999",
                        "604801",
                        "Fri, 23 Oct 2020 00:00:00 GMT")) {
            assertThat(
                            PlatformCooldown.delay(
                                    headers(Map.of("Retry-After", List.of(value))), clock, 60))
                    .isEqualTo(Duration.ofSeconds(60));
        }
        for (String value : List.of("bad", "1", Long.toString(Long.MAX_VALUE))) {
            assertThat(
                            PlatformCooldown.delay(
                                    headers(
                                            Map.of(
                                                    "X-RateLimit-Remaining",
                                                    List.of("0"),
                                                    "X-RateLimit-Reset",
                                                    List.of(value))),
                                    clock,
                                    60))
                    .isEqualTo(Duration.ofSeconds(60));
        }
        assertThat(PlatformCooldown.delay(headers(Map.of("Retry-After", List.of("2"))), clock, 60))
                .isEqualTo(Duration.ofSeconds(3));
    }
}
