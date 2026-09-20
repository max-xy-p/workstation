package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {
    @Mock
    private AuditOutboxWriter outboxWriter;
    @Mock
    private CurrentPrincipalResolver principalResolver;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(outboxWriter, principalResolver);
        when(principalResolver.require()).thenReturn(new CurrentPrincipal(
                "subject-1", PrincipalType.USER, "mike", "Mike", Set.of("WORKSTATION_USER")));
    }

    @Test
    void successWritesPendingEventIntoCurrentTransaction() {
        auditService.success(9L, "NOTE_UPDATE", "NOTE", 25L);

        ArgumentCaptor<AuditOutbox> captor = ArgumentCaptor.forClass(AuditOutbox.class);
        verify(outboxWriter).writeRequired(captor.capture());
        AuditOutbox event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.getEventId()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(event.getActorSubject()).isEqualTo("subject-1");
        org.assertj.core.api.Assertions.assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        org.assertj.core.api.Assertions.assertThat(event.getStatus()).isEqualTo(AuditOutboxStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(event.getAttempts()).isZero();
    }

    @Test
    void deniedOutboxFailureDoesNotReplaceAuthorizationResponse() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(outboxWriter).writeNewTransaction(any(AuditOutbox.class));

        assertThatCode(() -> auditService.denied(9L, "NOTE_WRITE", "WORKSPACE", 9L))
                .doesNotThrowAnyException();
    }
}
