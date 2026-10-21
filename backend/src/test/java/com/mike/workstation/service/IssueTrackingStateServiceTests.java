package com.mike.workstation.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.domain.*;
import com.mike.workstation.mapper.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;

class IssueTrackingStateServiceTests {
    private final IssueSourceMapper sources=mock(IssueSourceMapper.class);
    private final IntegrationMapper integrations=mock(IntegrationMapper.class);
    private final IntegrationStateService leases=mock(IntegrationStateService.class);
    private final IssueTrackingStateService state=new IssueTrackingStateService(sources,integrations,leases,mock(GithubWebhookMapper.class));
    private IntegrationRun run;
    private IssueSource source;
    private DevelopmentItem fresh;
    private ExternalRepository repo;
    @BeforeEach void setup() {
        run=new IntegrationRun();run.setWorkspaceId(9L);run.setConnectionId(2L);run.setId(3L);
        source=new IssueSource();source.setId(4L);source.setRepositoryId(6L);source.setProjectId(20L);source.setLinkToken("generation");
        source.setWorkspaceId(9L);source.setExternalId("100");source.setIssueNumber(7L);source.setTitle("原始标题");source.setStatus("OPEN");source.setSourceUpdatedAt(LocalDateTime.of(2026,10,1,1,0));
        fresh=new DevelopmentItem();fresh.setExternalId("100");fresh.setIssueNumber(7L);fresh.setTitle("最新标题");fresh.setStatus("CLOSED");fresh.setStateReason("completed");fresh.setSourceUpdatedAt(source.getSourceUpdatedAt().plusDays(1));
        repo=new ExternalRepository();repo.setConnectionId(2L);when(sources.lock(9L,4L)).thenReturn(source);when(sources.linkToken(9L,4L)).thenReturn("generation");
        when(sources.boundProject(9L,6L)).thenReturn(20L);when(integrations.repository(9L,6L)).thenReturn(repo);
    }
    @Test void beginFreezesUpperBoundAndPreservesResumeCursor() {
        when(sources.upperBound(9L)).thenReturn(99L);run.setIssueCursor(10);state.begin(run);assertThat(run.getIssueUpperBound()).isEqualTo(99L);assertThat(run.getIssueCursor()).isEqualTo(10);state.begin(run);verify(sources,times(1)).upperBound(9L);
    }
    @Test void updatesSourceAndPersistsProgress() {
        state.complete(run,source,fresh,null);assertThat(source.getStatus()).isEqualTo("CLOSED");assertThat(source.getLastSuccessAt()).isNotNull();assertThat(source.getLastError()).isNull();assertThat(run.getIssueCursor()).isEqualTo(4);assertThat(run.getProcessed()).isEqualTo(1);verify(integrations).updateRun(run);
    }
    @Test void olderResultDoesNotRollbackSnapshot() {
        fresh.setSourceUpdatedAt(source.getSourceUpdatedAt().minusDays(1));state.complete(run,source,fresh,null);assertThat(source.getTitle()).isEqualTo("原始标题");assertThat(source.getLastSuccessAt()).isNotNull();
    }
    @Test void equalTimestampContradictionReportsError() {
        fresh.setSourceUpdatedAt(source.getSourceUpdatedAt());state.complete(run,source,fresh,null);assertThat(source.getTitle()).isEqualTo("原始标题");assertThat(source.getLastError()).contains("不一致");assertThat(run.getFailed()).isEqualTo(1);
    }
    @Test void equalContentIsSuccessful() {
        fresh.setSourceUpdatedAt(source.getSourceUpdatedAt());fresh.setTitle(source.getTitle());fresh.setStatus(source.getStatus());fresh.setStateReason(null);state.complete(run,source,fresh,null);assertThat(source.getLastError()).isNull();
    }
    @Test void absentBaseTimestampAcceptsFreshSnapshot() { source.setSourceUpdatedAt(null);state.complete(run,source,fresh,null);assertThat(source.getStatus()).isEqualTo("CLOSED"); }
    @Test void missingFreshTimestampDoesNotReplaceKnownSnapshot() { fresh.setSourceUpdatedAt(null);state.complete(run,source,fresh,null);assertThat(source.getStatus()).isEqualTo("OPEN"); }
    @Test void failedReadPreservesSnapshotAndSuccessTime() {
        LocalDateTime success=LocalDateTime.of(2026,10,1,2,0);source.setLastSuccessAt(success);state.complete(run,source,null,"平台资源不可读取");assertThat(source.getTitle()).isEqualTo("原始标题");assertThat(source.getLastSuccessAt()).isEqualTo(success);assertThat(run.getFailed()).isEqualTo(1);
    }
    @Test void wrongIdentityOrNumberAndMissingResponseAreErrors() {
        fresh.setExternalId("other");state.complete(run,source,fresh,null);assertThat(source.getLastError()).contains("身份");fresh.setExternalId("100");fresh.setIssueNumber(8L);state.complete(run,source,fresh,null);assertThat(source.getTitle()).isEqualTo("原始标题");state.complete(run,source,null,null);assertThat(source.getLastError()).contains("身份");
    }
    @Test void detachedOrReattachedGenerationSkipsLateResponse() {
        when(sources.linkToken(9L,4L)).thenReturn(null,"new-generation");state.complete(run,source,fresh,null);state.complete(run,source,fresh,null);verify(sources,never()).update(any());assertThat(run.getIssueCursor()).isEqualTo(4);
    }
    @Test void repositoryMoveOrConnectionChangeStopsCommit() {
        when(sources.boundProject(9L,6L)).thenReturn(21L);state.complete(run,source,fresh,null);when(sources.boundProject(9L,6L)).thenReturn(20L);repo.setConnectionId(8L);state.complete(run,source,fresh,null);verify(sources,never()).update(any());
    }
    @Test void missingSourceOrRepositorySkipsCommit() {
        when(sources.lock(9L,4L)).thenReturn(null);state.complete(run,source,fresh,null);when(sources.lock(9L,4L)).thenReturn(source);when(integrations.repository(9L,6L)).thenReturn(null);state.complete(run,source,fresh,null);verify(sources,never()).update(any());
    }
    @Test void revokedLeaseStopsDataAndCursorWrites() {
        doThrow(new com.mike.workstation.exception.IntegrationConflictException("租约已失效")).when(leases).heartbeat(run);assertThatThrownBy(()->state.complete(run,source,fresh,null)).isInstanceOf(com.mike.workstation.exception.IntegrationConflictException.class);verifyNoInteractions(sources);assertThat(run.getIssueCursor()).isZero();
    }
}
