package com.mike.workstation.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

class MailPropertiesTests {
    private MailProperties properties(String replyTo) {
        return new MailProperties(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "noreply@example.com",
                "Workstation",
                replyTo,
                false,
                "");
    }

    @Test
    void replyToAcceptsSubdomainsAndOptionalEmptyValue() {
        for (String address : List.of("", "help@example.com", "name+tag@sub.example.com")) {
            assertEquals(address, properties(address).replyTo());
        }
    }

    @Test
    void replyToRejectsHeaderInjectionEmptyLabelsAndExcessiveLengthPromptly() {
        assertTimeout(
                Duration.ofSeconds(2),
                () -> {
                    for (String address :
                            List.of(
                                    "help@example.com\r\nBcc: other@example.com",
                                    "a@sub..example.com",
                                    "a@example.com.",
                                    "a".repeat(100_000) + "@example.com",
                                    "a@" + ".".repeat(100_000))) {
                        assertThrows(IllegalArgumentException.class, () -> properties(address));
                    }
                });
    }
}
