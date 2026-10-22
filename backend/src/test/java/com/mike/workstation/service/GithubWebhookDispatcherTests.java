package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.domain.GithubWebhook;
import com.mike.workstation.domain.GithubWebhookDelivery;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IntegrationRun;
import com.mike.workstation.domain.IssueSource;
import com.mike.workstation.mapper.GithubWebhookMapper;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.mapper.IssueSourceMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class GithubWebhookDispatcherTests {
    private final GithubWebhookMapper hooks = mock(GithubWebhookMapper.class);
    private final IntegrationMapper integrations = mock(IntegrationMapper.class);
    private final IssueSourceMapper sources = mock(IssueSourceMapper.class);
    private final IntegrationStateService state = mock(IntegrationStateService.class);
    private final GithubWebhookDispatcher dispatcher =
            new GithubWebhookDispatcher(hooks, integrations, sources, state);
    private GithubWebhook hook;
    private IntegrationConnection connection;
    private IssueSource source;

    private GithubWebhookDelivery delivery() {
        var d = new GithubWebhookDelivery();
        d.setId(8L);
        d.setWorkspaceId(9L);
        d.setConnectionId(2L);
        d.setWebhookVersion(1);
        d.setSourceId(4L);
        d.setLinkToken("generation");
        return d;
    }

    @BeforeEach
    void setup() {
        hook = new GithubWebhook();
        hook.setId("endpoint");
        hook.setEnabled(true);
        hook.setVersion(1);
        when(hooks.lock(9L, 2L)).thenReturn(hook);
        connection = new IntegrationConnection();
        connection.setWorkspaceId(9L);
        connection.setId(2L);
        connection.setEnabled(true);
        connection.setOwnerSubject("owner");
        connection.setVersion(1);
        when(integrations.lockConnection(9L, 2L)).thenReturn(connection);
        when(state.canManage(connection)).thenReturn(true);
        source = new IssueSource();
        source.setId(4L);
        source.setProjectId(20L);
        source.setLinkToken("generation");
        source.setTrackingStatus("ACTIVE");
        when(sources.tracked(9L, 4L)).thenReturn(source);
        when(integrations.insertRun(any()))
                .thenAnswer(
                        inv -> {
                            ((IntegrationRun) inv.getArgument(0)).setId(7L);
                            return 1;
                        });
    }

    @Test
    void notificationsUseOneExistingQueueRun() {
        var a = delivery();
        var b = delivery();
        when(hooks.pending(9L, 2L)).thenReturn(List.of(a, b));
        dispatcher.dispatch(9L, 2L);
        verify(integrations, times(1)).insertRun(any());
        assertThat(a.getStatus()).isEqualTo("QUEUED");
        assertThat(b.getRunId()).isEqualTo(7L);
    }

    @Test
    void activeRunLeavesNewNotificationsPending() {
        when(integrations.active(9L, 2L)).thenReturn(new IntegrationRun());
        dispatcher.dispatch(9L, 2L);
        verify(hooks, never()).pending(9L, 2L);
        verify(integrations, never()).insertRun(any());
    }

    @Test
    void changedGenerationAndConfigurationAreIgnored() {
        var a = delivery();
        var b = delivery();
        b.setWebhookVersion(0);
        source.setLinkToken("new-generation");
        when(hooks.pending(9L, 2L)).thenReturn(List.of(a, b));
        dispatcher.dispatch(9L, 2L);
        assertThat(a.getStatus()).isEqualTo("IGNORED");
        assertThat(b.getStatus()).isEqualTo("IGNORED");
        verify(integrations, never()).insertRun(any());
    }

    @Test
    void revokedConnectionCancelsWork() {
        when(state.canManage(connection)).thenReturn(false);
        dispatcher.dispatch(9L, 2L);
        verify(hooks).cancelRuns(9L, 2L);
        verify(hooks).cancelDeliveries(9L, 2L);
    }

    @Test
    void failedTargetsRetryOnlyThreeRoundsAndSuccessfulTargetsSettle() {
        var retry = delivery();
        retry.setAttempts(1);
        retry.setRunStatus("PARTIAL");
        retry.setTargetStatus("FAILED");
        retry.setTargetError("读取失败");
        var failed = delivery();
        failed.setAttempts(3);
        failed.setRunStatus("FAILED");
        var success = delivery();
        success.setRunStatus("SUCCEEDED");
        success.setTargetStatus("SUCCEEDED");
        var cancelled = delivery();
        cancelled.setRunStatus("CANCELLED");
        var ignored = delivery();
        ignored.setRunStatus("SUCCEEDED");
        ignored.setTargetStatus("CANCELLED");
        when(hooks.settled(9L, 2L)).thenReturn(List.of(retry, failed, success, cancelled, ignored));
        dispatcher.dispatch(9L, 2L);
        assertThat(retry.getStatus()).isEqualTo("RETRY");
        assertThat(retry.getNextAttemptAt()).isNotNull();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(success.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(ignored.getStatus()).isEqualTo("IGNORED");
    }
}
