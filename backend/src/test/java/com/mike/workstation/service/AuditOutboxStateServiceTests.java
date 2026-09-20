package com.mike.workstation.service;

import static org.mockito.Mockito.verify;

import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.mapper.AuditOutboxMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditOutboxStateServiceTests {
    @Mock
    private AuditOutboxMapper outboxMapper;

    @Test
    void exhaustedEventMovesToFailedInsteadOfBeingRetriedForever() {
        AuditOutboxProperties properties = new AuditOutboxProperties(
                100, Duration.ofSeconds(30), Duration.ofSeconds(5), 3, Duration.ofSeconds(5));
        AuditOutbox event = new AuditOutbox();
        event.setId(7L);
        event.setEventId("event-7");
        event.setLockedBy("relay-1");
        event.setAttempts(3);
        org.mockito.Mockito.when(outboxMapper.markFailure(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("relay-1"),
                org.mockito.ArgumentMatchers.eq(AuditOutboxStatus.FAILED),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq("still failing")))
                .thenReturn(1);

        new AuditOutboxStateService(outboxMapper, properties)
                .markFailure(event, new IllegalStateException("still failing"));

        ArgumentCaptor<LocalDateTime> nextAttempt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper).markFailure(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("relay-1"),
                org.mockito.ArgumentMatchers.eq(AuditOutboxStatus.FAILED),
                nextAttempt.capture(),
                org.mockito.ArgumentMatchers.eq("still failing"));
    }
}
