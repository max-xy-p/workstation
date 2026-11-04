package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.domain.Notification;
import com.mike.workstation.domain.NotificationResourceType;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.dto.NotificationReadAllRequest;
import com.mike.workstation.dto.NotificationState;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.NotificationMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class NotificationServiceTests {
    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final CurrentPrincipalResolver principal = mock(CurrentPrincipalResolver.class);
    private final NotificationService service = new NotificationService(mapper, principal,
            new NotificationProperties(true, 500, 5, 90));

    NotificationServiceTests() {
        when(principal.require()).thenReturn(new CurrentPrincipal("user", PrincipalType.USER, "用户", null, Set.of()));
    }

    @Test
    void disabledFeatureDoesNotReadDatabaseAndRejectsMachineIdentity() {
        var disabled = new NotificationService(mapper, principal, new NotificationProperties(false, 500, 5, 90));
        assertThat(disabled.state()).isEqualTo(new NotificationState(false, 0, 0, 0));
        assertThat(disabled.list(0, 20, false, null).items()).isEmpty();
        verifyNoInteractions(mapper);
        assertThatThrownBy(() -> disabled.get(UUID.randomUUID().toString())).isInstanceOf(NotFoundException.class);
        when(principal.require()).thenReturn(new CurrentPrincipal("machine", PrincipalType.SERVICE, "机器", null, Set.of()));
        assertThatThrownBy(disabled::state).isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    @Test
    void paginationUsesSubmittedUpperBoundAndCentralResponseAssembler() {
        when(mapper.state("user")).thenReturn(new NotificationState(true, 2, 10, 11));
        Notification notification = notification();
        when(mapper.list("user", 7, true, 20, 20)).thenReturn(List.of(notification));
        when(mapper.count("user", 7, true)).thenReturn(21L);
        var page = service.list(1, 20, true, 7L);
        assertThat(page.total()).isEqualTo(21);
        assertThat(page.items().get(0).title()).isEqualTo("测试通知");
        assertThat(page.hasMore()).isFalse();
        when(mapper.list("user", 10, false, 0, 20)).thenReturn(List.of());
        service.list(0, 20, false, 999L);
        verify(mapper).list("user", 10, false, 0, 20);
        assertThatThrownBy(() -> service.list(-1, 20, false, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(0, 101, false, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(0, 20, false, -1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void scopedGetAndIdempotentReadNeverExposeOtherRecipients() {
        Notification notification = notification();
        when(mapper.find("user", notification.getId())).thenReturn(notification);
        assertThat(service.get(notification.getId()).id()).isEqualTo(notification.getId());
        when(mapper.read(eq("user"), eq(notification.getId()), any())).thenReturn(1, 0);
        service.read(notification.getId());
        service.read(notification.getId());
        verify(mapper, times(1)).changed("user");
        assertThatThrownBy(() -> service.get(UUID.randomUUID().toString())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.read(UUID.randomUUID().toString())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void readAllClampsToInboxAndSignalsOnlyActualChanges() {
        assertThatThrownBy(() -> service.readAll(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.readAll(new NotificationReadAllRequest(-1L))).isInstanceOf(BusinessException.class);
        when(mapper.lockInbox("user")).thenReturn(null);
        service.readAll(new NotificationReadAllRequest(0L));
        verify(mapper, never()).readAll(any(), anyLong(), any());
        when(mapper.lockInbox("user")).thenReturn(10L);
        when(mapper.readAll(eq("user"), eq(7L), any())).thenReturn(2);
        service.readAll(new NotificationReadAllRequest(7L));
        service.readAll(new NotificationReadAllRequest(999L));
        verify(mapper).readAll(eq("user"), eq(10L), any());
        verify(mapper).changed("user");
    }

    private Notification notification() {
        Notification n = new Notification();
        n.setId(UUID.randomUUID().toString());
        n.setSequence(1);
        n.setWorkspaceId(9L);
        n.setType("TEST_EVENT");
        n.setTitle("测试通知");
        n.setBody("纯文本");
        n.setResourceType(NotificationResourceType.WORKSPACE);
        n.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return n;
    }
}
