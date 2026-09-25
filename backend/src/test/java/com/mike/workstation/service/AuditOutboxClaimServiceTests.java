package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.mapper.AuditOutboxMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditOutboxClaimServiceTests {
    @Mock
    AuditOutboxMapper outboxMapper;
    private final AuditOutboxProperties properties = new AuditOutboxProperties(
            10, Duration.ofMinutes(2), Duration.ofSeconds(5), 3, Duration.ofSeconds(10));

    @Test
    void emptyBatchDoesNotCreateLease() {
        var result = new AuditOutboxClaimService(outboxMapper, properties).claimBatch();

        assertThat(result).isEmpty();
        verify(outboxMapper, never()).markSending(any(), any(), any());
    }

    @Test
    void claimedEventsReceiveSameLeaseAndIncrementAttempts() {
        AuditOutbox first = event(1L, 0);
        AuditOutbox second = event(2L, 2);
        when(outboxMapper.findClaimable(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(first, second));
        when(outboxMapper.markSending(eq(List.of(1L, 2L)), any(), any(LocalDateTime.class)))
                .thenReturn(2);

        var claimed = new AuditOutboxClaimService(outboxMapper, properties).claimBatch();

        assertThat(claimed).containsExactly(first, second);
        assertThat(first.getStatus()).isEqualTo(AuditOutboxStatus.SENDING);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getAttempts()).isEqualTo(3);
        assertThat(first.getLockedBy()).isNotBlank().isEqualTo(second.getLockedBy());
        assertThat(first.getLockedUntil()).isEqualTo(second.getLockedUntil());
    }

    @Test
    void partialLeaseUpdateFailsWithoutMarkingEventsInMemory() {
        AuditOutbox event = event(1L, 0);
        when(outboxMapper.findClaimable(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(event));

        assertThatThrownBy(() -> new AuditOutboxClaimService(outboxMapper, properties).claimBatch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数量不一致");
        assertThat(event.getStatus()).isNull();
        assertThat(event.getLockedBy()).isNull();
    }

    private AuditOutbox event(Long id, int attempts) {
        AuditOutbox event = new AuditOutbox();
        event.setId(id);
        event.setAttempts(attempts);
        return event;
    }
}
