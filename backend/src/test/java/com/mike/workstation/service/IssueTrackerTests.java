package com.mike.workstation.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.domain.*;
import com.mike.workstation.exception.*;
import com.mike.workstation.integration.RepositoryProvider;
import com.mike.workstation.mapper.*;
import java.util.List;
import org.junit.jupiter.api.*;

class IssueTrackerTests {
    private final IssueSourceMapper sources=mock(IssueSourceMapper.class);
    private final IntegrationMapper integrations=mock(IntegrationMapper.class);
    private final IssueTrackingStateService state=mock(IssueTrackingStateService.class);
    private final IntegrationStateService leases=mock(IntegrationStateService.class);
    private final RepositoryProvider provider=mock(RepositoryProvider.class);
    private final IssueTracker tracker=new IssueTracker(sources,integrations,state,leases,mock(GithubWebhookMapper.class));
    private final IntegrationRun run=new IntegrationRun();
    private final IntegrationConnection connection=new IntegrationConnection();
    private final IssueSource source=new IssueSource();
    @BeforeEach void setup() {
        run.setWorkspaceId(9L);run.setIssueCursor(5);run.setIssueUpperBound(99L);connection.setId(2L);connection.setProvider("GITHUB");source.setId(6L);source.setRepositoryId(7L);source.setIssueNumber(8L);when(sources.pending(9L,2L,5,99)).thenReturn(List.of(source),List.of());
    }
    @Test void resumesFromCursorOutsideRecentWindow() {
        ExternalRepository repo=new ExternalRepository();when(integrations.repository(9L,7L)).thenReturn(repo);DevelopmentItem item=new DevelopmentItem();when(provider.issue(connection,"token",repo,8)).thenReturn(item);tracker.track(run,connection,provider,"token");verify(state).complete(run,source,item,null);
    }
    @Test void failedReadReportsSanitizedResult() {
        ExternalRepository repo=new ExternalRepository();when(integrations.repository(9L,7L)).thenReturn(repo);when(provider.issue(connection,"token",repo,8)).thenThrow(new IllegalStateException("sensitive"));tracker.track(run,connection,provider,"token");verify(state).complete(eq(run),eq(source),isNull(),eq("同步失败，请检查连接配置或联系管理员"));
    }
    @Test void missingRepositoryAndLeaseLossHandled() {
        tracker.track(run,connection,provider,"token");verify(state).complete(run,source,null,"来源仓库不可读取");doThrow(new IntegrationConflictException("租约失效")).when(leases).heartbeat(run);when(sources.pending(9L,2L,5,99)).thenReturn(List.of(source));assertThatThrownBy(()->tracker.track(run,connection,provider,"token")).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void unsupportedProviderDoesNotStartTracking() { connection.setProvider("GITLAB");tracker.track(run,connection,provider,"token");verifyNoInteractions(state,sources); }
}
