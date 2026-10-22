package com.mike.workstation.service;

import static org.mockito.Mockito.verify;

import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.mapper.AuditOutboxMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class AuditOutboxStateServiceTests {
    @Mock private AuditOutboxMapper outboxMapper;

    @Test
    void exhaustedEventMovesToFailedInsteadOfBeingRetriedForever() {
        AuditOutboxProperties properties =
                new AuditOutboxProperties(
                        100,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        3,
                        Duration.ofSeconds(5));
        AuditOutbox event = new AuditOutbox();
        event.setId(7L);
        event.setEventId("event-7");
        event.setLockedBy("relay-1");
        event.setAttempts(3);
        Mockito.when(
                        outboxMapper.markFailure(
                                ArgumentMatchers.eq(7L),
                                ArgumentMatchers.eq("relay-1"),
                                ArgumentMatchers.eq(AuditOutboxStatus.FAILED),
                                ArgumentMatchers.any(LocalDateTime.class),
                                ArgumentMatchers.eq("still failing")))
                .thenReturn(1);

        new AuditOutboxStateService(outboxMapper, properties)
                .markFailure(event, new IllegalStateException("still failing"));

        ArgumentCaptor<LocalDateTime> nextAttempt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper)
                .markFailure(
                        ArgumentMatchers.eq(7L),
                        ArgumentMatchers.eq("relay-1"),
                        ArgumentMatchers.eq(AuditOutboxStatus.FAILED),
                        nextAttempt.capture(),
                        ArgumentMatchers.eq("still failing"));
    }
}
