package com.mike.workstation.keycloak.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.keycloak.models.SingleUseObjectProvider;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

class EmailCodeServiceTests {
    static final class TestClock extends Clock {
        final AtomicLong seconds = new AtomicLong(1_800_000_000L);

        void advance(long value) {
            seconds.addAndGet(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochSecond(seconds.get());
        }
    }

    record Entry(Map<String, String> notes, long expires) {}

    static final class Fixture {
        final SecretKey key = new SecretKeySpec(new SecureRandom().generateSeed(32), "HmacSHA256");
        final TestClock clock = new TestClock();
        final Map<String, Entry> data = new ConcurrentHashMap<>();
        final SingleUseObjectProvider store = mock(SingleUseObjectProvider.class);
        final EmailCodeService service;

        Fixture() {
            doAnswer(
                            call -> {
                                data.put(
                                        call.getArgument(0),
                                        new Entry(
                                                Map.copyOf(call.getArgument(2)),
                                                clock.instant().getEpochSecond()
                                                        + (long) call.getArgument(1)));
                                return null;
                            })
                    .when(store)
                    .put(anyString(), anyLong(), anyMap());
            when(store.get(anyString()))
                    .thenAnswer(
                            call -> {
                                Entry entry = data.get(call.getArgument(0));
                                return entry == null
                                                || entry.expires()
                                                        <= clock.instant().getEpochSecond()
                                        ? null
                                        : entry.notes();
                            });
            when(store.remove(anyString()))
                    .thenAnswer(
                            call -> {
                                Entry entry = data.remove(call.getArgument(0));
                                return entry == null
                                                || entry.expires()
                                                        <= clock.instant().getEpochSecond()
                                        ? null
                                        : entry.notes();
                            });
            when(store.putIfAbsent(anyString(), anyLong()))
                    .thenAnswer(
                            call -> {
                                String key = call.getArgument(0);
                                Entry fresh =
                                        new Entry(
                                                Map.of(),
                                                clock.instant().getEpochSecond()
                                                        + (long) call.getArgument(1));
                                data.computeIfPresent(
                                        key,
                                        (ignored, entry) ->
                                                entry.expires() <= clock.instant().getEpochSecond()
                                                        ? null
                                                        : entry);
                                return data.putIfAbsent(key, fresh) == null;
                            });
            service = new EmailCodeService(store, clock, new SecureRandom(), key);
        }
    }

    private final EmailCodeService.Binding binding =
            new EmailCodeService.Binding(
                    "realm", "user", "a@example.com", "session|tab|client", "VERIFY");

    @Test
    void storesOnlySaltedDigestAndConsumesCodeOnce() {
        Fixture fixture = new Fixture();
        var code = fixture.service.issue(binding, null, false, "127.0.0.1");
        assertTrue(code.value().matches("[0-9]{6}"));
        assertFalse(code.toString().contains(code.value()));
        assertTrue(
                fixture.data.values().stream()
                        .flatMap(entry -> entry.notes().values().stream())
                        .noneMatch(value -> value.equals(code.value())));
        assertTrue(fixture.service.verify(binding, code.id(), code.value()));
        assertFalse(fixture.service.verify(binding, code.id(), code.value()));
    }

    @Test
    void bindsUserEmailPurposeAndAuthenticationSession() {
        Fixture fixture = new Fixture();
        var code = fixture.service.issue(binding, null, false, "127.0.0.1");
        for (var other :
                List.of(
                        new EmailCodeService.Binding(
                                "other", "user", "a@example.com", "session|tab|client", "VERIFY"),
                        new EmailCodeService.Binding(
                                "realm", "other", "a@example.com", "session|tab|client", "VERIFY"),
                        new EmailCodeService.Binding(
                                "realm", "user", "b@example.com", "session|tab|client", "VERIFY"),
                        new EmailCodeService.Binding(
                                "realm", "user", "a@example.com", "other-tab", "VERIFY"),
                        new EmailCodeService.Binding(
                                "realm", "user", "a@example.com", "session|tab|client", "RESET"))) {
            assertFalse(fixture.service.verify(other, code.id(), code.value()));
        }
        assertTrue(fixture.service.verify(binding, code.id(), code.value()));
    }

    @Test
    void expiryAndAttemptLimitRejectEvenCorrectCode() {
        Fixture expired = new Fixture();
        var code = expired.service.issue(binding, null, false, "127.0.0.1");
        expired.clock.advance(300);
        assertFalse(expired.service.verify(binding, code.id(), code.value()));
        Fixture exhausted = new Fixture();
        var another = exhausted.service.issue(binding, null, false, "127.0.0.1");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(exhausted.service.verify(binding, another.id(), "bad"));
        }
        assertFalse(exhausted.service.verify(binding, another.id(), another.value()));
        assertFalse(exhausted.service.verify(binding, null, another.value()));
    }

    @Test
    void refreshDoesNotResendAndCooldownCannotBeBypassedByAnotherSession() {
        Fixture fixture = new Fixture();
        var code = fixture.service.issue(binding, null, false, "127.0.0.1");
        assertNull(fixture.service.issue(binding, code.id(), false, "127.0.0.1"));
        assertThrows(
                IllegalStateException.class,
                () -> fixture.service.issue(binding, code.id(), true, "127.0.0.1"));
        var other =
                new EmailCodeService.Binding(
                        "realm", "user", "a@example.com", "other-tab", "VERIFY");
        assertThrows(
                IllegalStateException.class,
                () -> fixture.service.issue(other, null, false, "127.0.0.1"));
        fixture.clock.advance(60);
        var replacement = fixture.service.issue(binding, code.id(), true, "127.0.0.1");
        assertFalse(fixture.service.verify(binding, code.id(), code.value()));
        assertTrue(fixture.service.verify(binding, replacement.id(), replacement.value()));
    }

    @Test
    void accountQuotaSurvivesNewSessionsAndInvalidation() {
        Fixture fixture = new Fixture();
        for (int index = 0; index < 5; index++) {
            var code = fixture.service.issue(binding, null, true, "127.0.0.1");
            fixture.service.invalidate(code.id());
            fixture.clock.advance(60);
        }
        assertThrows(
                IllegalStateException.class,
                () -> fixture.service.issue(binding, null, true, "127.0.0.1"));
    }

    @Test
    void ipQuotaLimitsSendingAcrossAccountsAndPurposes() {
        Fixture fixture = new Fixture();
        for (int index = 0; index < 20; index++) {
            var user =
                    new EmailCodeService.Binding(
                            "realm", "user-" + index, "a@example.com", "tab", "VERIFY");
            fixture.service.issue(user, null, false, "127.0.0.1");
        }
        var blocked =
                new EmailCodeService.Binding("realm", "another", "a@example.com", "tab", "RESET");
        assertThrows(
                IllegalStateException.class,
                () -> fixture.service.issue(blocked, null, false, "127.0.0.1"));
    }

    @Test
    void concurrentCorrectSubmissionsHaveExactlyOneWinner() throws Exception {
        Fixture fixture = new Fixture();
        var code = fixture.service.issue(binding, null, false, "127.0.0.1");
        try (var pool = Executors.newFixedThreadPool(12)) {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                attempts.add(() -> fixture.service.verify(binding, code.id(), code.value()));
            }
            int successes = 0;
            for (var result : pool.invokeAll(attempts)) {
                if (result.get()) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }
    }
}
