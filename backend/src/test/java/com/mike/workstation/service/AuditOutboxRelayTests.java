package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.messaging.AuditMessagePublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditOutboxRelayTests {
    @Mock
    private AuditOutboxClaimService claimService;
    @Mock
    private AuditMessagePublisher publisher;
    @Mock
    private AuditOutboxStateService stateService;

    @Test
    void oneMessageFailureDoesNotRetrySuccessfulMessagesAsWholeBatch() throws Exception {
        AuditOutbox first = event(1L, "event-1");
        AuditOutbox second = event(2L, "event-2");
        IllegalStateException failure = new IllegalStateException("broker unavailable");
        when(claimService.claimBatch()).thenReturn(List.of(first, second));
        doNothing().when(publisher).publish(first);
        doThrow(failure).when(publisher).publish(second);

        AuditRelayResult result = new AuditOutboxRelay(claimService, publisher, stateService).relayBatch();

        assertThat(result).isEqualTo(new AuditRelayResult(2, 1, 1));
        verify(stateService).markPublished(first);
        verify(stateService).markFailure(second, failure);
    }

    private AuditOutbox event(Long id, String eventId) {
        AuditOutbox event = new AuditOutbox();
        event.setId(id);
        event.setEventId(eventId);
        event.setAttempts(1);
        event.setLockedBy("relay-1");
        return event;
    }
}
