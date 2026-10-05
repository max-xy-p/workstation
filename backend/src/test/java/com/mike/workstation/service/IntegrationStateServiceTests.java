package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.integration.CredentialCipher;
import com.mike.workstation.mapper.*;
import com.mike.workstation.security.*;
import com.mike.workstation.exception.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationStateServiceTests {
    private final IntegrationMapper mapper=mock(IntegrationMapper.class);
    private final ProjectMapper projects=mock(ProjectMapper.class);
    private final WorkspaceMapper workspaces=mock(WorkspaceMapper.class);
    private final WorkspaceAuthorizationService authorization=mock(WorkspaceAuthorizationService.class);
    private final CurrentPrincipalResolver principal=mock(CurrentPrincipalResolver.class);
    private final CredentialCipher cipher=mock(CredentialCipher.class);
    private final AuditService audit=mock(AuditService.class);
    private final IntegrationProperties properties=new IntegrationProperties(true,"v1",Map.of(),List.of(),15,10,100,180,30);
    private final IntegrationStateService state=new IntegrationStateService(mapper,projects,workspaces,authorization,principal,cipher,properties,audit);
    private IntegrationConnection c;
    private IntegrationRun run;
    @BeforeEach void setup() {
        c=new IntegrationConnection();c.setId(2L);c.setWorkspaceId(9L);c.setProvider("GITHUB");c.setBaseUrl("https://github.com");
        c.setAccount("owner");c.setOwnerSubject("owner");c.setEnabled(true);c.setVersion(1);c.setScope("OWNED");
        run=new IntegrationRun();run.setId(3L);run.setWorkspaceId(9L);run.setConnectionId(2L);run.setConfigVersion(1);
        run.setActorSubject("owner");run.setStatus("RUNNING");run.setLeaseToken("lease");run.setKind("IMPORT");
        when(mapper.lockConnection(9L,2L)).thenReturn(c);when(mapper.lockRun(run)).thenReturn(run);
        WorkspaceMember member=new WorkspaceMember();member.setRole(WorkspaceRole.OWNER);
        when(workspaces.findMember(9L,"owner")).thenReturn(member);
        Workspace workspace=new Workspace();workspace.setType(WorkspaceType.PERSONAL);
        when(authorization.require(anyLong(),any())).thenReturn(workspace);
        when(principal.require()).thenReturn(new CurrentPrincipal("owner",PrincipalType.USER,"owner",null,Set.of()));
        when(projects.findById(eq(9L),anyLong())).thenReturn(new Project());
        when(projects.insert(any())).thenAnswer(inv->{ Project p=inv.getArgument(0);p.setId(20L);return 1; });
    }
    private IntegrationConnectionRequest request(Integer version,String token,boolean confirmed) {
        return new IntegrationConnectionRequest("连接",IntegrationProvider.GITHUB,"https://github.com",token,IntegrationScope.OWNED,
                false,false,false,60,version,confirmed);
    }
    private ExternalRepository repository(Long project) {
        ExternalRepository r=new ExternalRepository();r.setId(4L);r.setConnectionId(2L);r.setName("仓库");r.setProjectId(project);
        when(mapper.repository(9L,4L)).thenReturn(r);return r;
    }
    private IntegrationRunItem item(Long project) {
        IntegrationRunItem i=new IntegrationRunItem();i.setId(5L);i.setWorkspaceId(9L);i.setRunId(3L);i.setRepositoryId(4L);
        i.setProjectId(project);i.setProjectStatus("PLANNING");i.setStatus("QUEUED");return i;
    }
    @Test void saveCreatesEncryptedConnectionAndAuditsInSameTransaction() {
        when(cipher.encrypt(eq("token"),anyString())).thenReturn("encrypted");
        when(mapper.insertConnection(any())).thenAnswer(inv->{ ((IntegrationConnection)inv.getArgument(0)).setId(7L);return 1; });
        IntegrationConnection fresh=new IntegrationConnection();fresh.setWorkspaceId(9L);fresh.setProvider("GITHUB");fresh.setBaseUrl("https://github.com");
        state.saveConnection(9L,null,request(null,"token",false),fresh,"token");
        assertThat(fresh.getCredential()).isEqualTo("encrypted");assertThat(fresh.getVersion()).isEqualTo(1);
        assertThat(fresh.getOwnerSubject()).isEqualTo("owner");assertThat(fresh.getNextSyncAt()).isAfter(LocalDateTime.now());
        verify(audit).success(9L,"INTEGRATION_SAVE","INTEGRATION",7L);
    }
    @Test void updateCancelsOldRunsAndIncrementsConfigVersion() {
        state.saveConnection(9L,2L,request(1,null,false),c,"token");
        assertThat(c.getVersion()).isEqualTo(2);verify(mapper).cancel(9L,2L);verify(mapper).updateConnection(c);
    }
    @Test void rejectsStaleConfigurationAndUnconfirmedSharing() {
        assertThatThrownBy(()->state.saveConnection(9L,2L,request(0,"token",false),c,"token")).isInstanceOf(IntegrationConflictException.class);
        Workspace shared=new Workspace();shared.setType(WorkspaceType.SHARED);when(authorization.require(anyLong(),any())).thenReturn(shared);
        assertThatThrownBy(()->state.saveConnection(9L,null,request(null,"token",false),c,"token")).hasMessageContaining("成员可见");
        verify(mapper,never()).insertConnection(any());
    }
    @Test void pauseCancelsAndEnableRequiresOwnerStillAuthorized() {
        state.enabled(9L,2L,new IntegrationEnableRequest(false,1,false));
        assertThat(c.isEnabled()).isFalse();verify(mapper).cancel(9L,2L);
        when(workspaces.findMember(9L,"owner")).thenReturn(null);
        assertThatThrownBy(()->state.enabled(9L,2L,new IntegrationEnableRequest(true,2,false))).hasMessageContaining("授权已失效");
    }
    @Test void pauseRejectsConcurrentUpdates() {
        assertThatThrownBy(()->state.enabled(9L,2L,new IntegrationEnableRequest(false,0,false))).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void deletingConnectionDetachesSnapshotsAndKeepsProjectData() {
        state.delete(9L,2L);verify(mapper).detachConnection(9L,2L);verify(mapper).deleteConnection(9L,2L);
        verify(projects,never()).delete(anyLong(),anyLong());
        c.setDeleted(true);
        assertThatThrownBy(()->state.delete(9L,2L)).isInstanceOf(NotFoundException.class);
    }
    @Test void enqueuePersistsSelectionAndRejectsOtherConnection() {
        repository(null);
        var request=new IntegrationImportRequest("key",List.of(new IntegrationImportItemRequest(4L,null,ProjectStatus.PLANNING)),false);
        when(mapper.insertRun(any())).thenAnswer(inv->{ ((IntegrationRun)inv.getArgument(0)).setId(3L);return 1; });
        IntegrationRun queued=state.enqueue(9L,2L,"IMPORT","key",request);
        assertThat(queued.getStatus()).isEqualTo("QUEUED");verify(mapper).insertItem(any());verify(authorization).require(9L,WorkspacePermission.PROJECT_WRITE);
        repository(null).setConnectionId(8L);
        assertThatThrownBy(()->state.enqueue(9L,2L,"IMPORT","different",request)).hasMessageContaining("不属于");
    }
    @Test void duplicateRequestReturnsExistingAndDifferentContentConflicts() {
        when(mapper.insertRun(any())).thenAnswer(inv->{ IntegrationRun r=inv.getArgument(0);r.setId(3L);return 1; });
        IntegrationRun first=state.enqueue(9L,2L,"DISCOVER","same",null);
        when(mapper.request(9L,2L,"owner","same")).thenReturn(first);
        assertThat(state.enqueue(9L,2L,"DISCOVER","same",null)).isSameAs(first);
        assertThatThrownBy(()->state.enqueue(9L,2L,"SYNC","same",null)).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void preventsConcurrentRequestsAndMalformedKeys() {
        when(mapper.active(9L,2L)).thenReturn(run);
        assertThatThrownBy(()->state.enqueue(9L,2L,"SYNC","key",null)).isInstanceOf(IntegrationConflictException.class);
        assertThatThrownBy(()->state.enqueue(9L,2L,"SYNC","bad key",null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->state.enqueue(9L,2L,"SYNC",null,null)).isInstanceOf(BusinessException.class);
        c.setEnabled(false);
        assertThatThrownBy(()->state.enqueue(9L,2L,"SYNC","key",null)).hasMessageContaining("未启用");
    }
    @Test void rejectsEmptyDuplicateAndCrossWorkspaceImportSelections() {
        assertThatThrownBy(()->state.enqueue(9L,2L,"IMPORT","k",new IntegrationImportRequest("k",List.of(),false))).isInstanceOf(BusinessException.class);
        var i=new IntegrationImportItemRequest(4L,null,ProjectStatus.PLANNING);
        assertThatThrownBy(()->state.enqueue(9L,2L,"IMPORT","k",new IntegrationImportRequest("k",List.of(i,i),false))).hasMessageContaining("重复");
        assertThatThrownBy(()->state.enqueue(9L,2L,"IMPORT","k",new IntegrationImportRequest("k",List.of(i),false))).isInstanceOf(NotFoundException.class);
    }
    @Test void schedulerReusesQueueAndPausesConnectionsWhoseOwnerLeft() {
        c.setScheduleMinutes(60);c.setNextSyncAt(LocalDateTime.now().minusMinutes(1));
        state.schedule(9L,2L);
        ArgumentCaptor<IntegrationRun> captor=ArgumentCaptor.forClass(IntegrationRun.class);verify(mapper).insertRun(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("SYNC");assertThat(captor.getValue().getActorSubject()).isEqualTo("owner");
        c.setNextSyncAt(LocalDateTime.now().minusMinutes(1));when(workspaces.findMember(9L,"owner")).thenReturn(null);
        state.schedule(9L,2L);assertThat(c.isEnabled()).isFalse();
    }
    @Test void schedulerSkipsFutureAndAlreadyRunningConnections() {
        c.setScheduleMinutes(60);c.setNextSyncAt(LocalDateTime.now().plusMinutes(1));state.schedule(9L,2L);
        c.setNextSyncAt(LocalDateTime.now().minusMinutes(1));when(mapper.active(9L,2L)).thenReturn(run);state.schedule(9L,2L);
        verify(mapper,never()).insertRun(any());
    }
    @Test void leaseClaimAndHeartbeatFenceOldExecutors() {
        assertThat(state.claim()).isNull();
        when(mapper.nextRun()).thenReturn(run);assertThat(state.claim().getLeaseToken()).isNotBlank();
        state.heartbeat(run);verify(mapper).updateRun(run);
        when(mapper.lockRun(run)).thenReturn(null);
        assertThatThrownBy(()->state.heartbeat(run)).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void configurationAndActorRoleChangesPreventWrites() {
        c.setVersion(2);
        assertThatThrownBy(()->state.saveRepositories(run,List.of())).isInstanceOf(IntegrationConflictException.class);
        c.setVersion(1);WorkspaceMember viewer=new WorkspaceMember();viewer.setRole(WorkspaceRole.VIEWER);
        when(workspaces.findMember(9L,"owner")).thenReturn(viewer);
        assertThatThrownBy(()->state.heartbeat(run)).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void refreshUpdatesSelectedArchivedRepositoryAndSuppressedImportDoesNotRecreateProjects() {
        c.setAutoImport(true); ExternalRepository r=repository(null); r.setArchived(true); r.setAutoImportSuppressed(true);
        when(mapper.source(r)).thenReturn(r);
        state.refreshRepository(run,r);
        verify(mapper).updateRepository(r);verify(projects,never()).insert(any());
    }
    @Test void discoveryUpdatesStableIdentityWithoutTouchingLocalProject() {
        ExternalRepository r=repository(20L);r.setExternalId("source");r.setWorkspaceId(9L);r.setProvider("GITHUB");r.setBaseUrl("https://github.com");
        when(mapper.source(r)).thenReturn(r);
        state.saveRepositories(run,List.of(r));
        verify(mapper).updateRepository(r);verify(projects,never()).update(any());assertThat(run.getProcessed()).isEqualTo(1);
    }
    @Test void discoverySkipsExcludedAndOtherConnectionsResources() {
        ExternalRepository fork=repository(null);fork.setFork(true);state.saveRepositories(run,List.of(fork));verify(mapper,never()).insertRepository(any());
        ExternalRepository archived=new ExternalRepository();archived.setArchived(true);state.saveRepositories(run,List.of(archived));
        ExternalRepository r=new ExternalRepository();r.setName("new");ExternalRepository other=new ExternalRepository();other.setConnectionId(99L);
        when(mapper.source(r)).thenReturn(other);state.saveRepositories(run,List.of(r));verify(mapper,never()).updateRepository(any());
    }
    @Test void explicitAutoImportCreatesOnePlanningProject() {
        c.setAutoImport(true);ExternalRepository r=repository(null);
        when(mapper.insertRepository(any())).thenAnswer(inv->{ ((ExternalRepository)inv.getArgument(0)).setId(4L);return 1; });
        state.saveRepositories(run,List.of(r));verify(mapper).bind(9L,20L,4L);
        ArgumentCaptor<Project> p=ArgumentCaptor.forClass(Project.class);verify(projects).insert(p.capture());
        assertThat(p.getValue().getStatus()).isEqualTo(ProjectStatus.PLANNING);
    }
    @Test void importReusesExistingBindingWithoutRecreatingProject() {
        repository(20L);IntegrationRunItem item=item(null);state.importItem(run,item);
        assertThat(item.getProjectId()).isEqualTo(20L);assertThat(item.getStatus()).isEqualTo("SUCCEEDED");
        verify(projects,never()).insert(any());verify(mapper,never()).bind(anyLong(),anyLong(),anyLong());
    }
    @Test void importCreatesOrLinksSelectedProjectAtomically() {
        repository(null);IntegrationRunItem i=item(null);state.importItem(run,i);
        verify(mapper).bind(9L,20L,4L);assertThat(i.getProjectId()).isEqualTo(20L);
        IntegrationRunItem second=item(30L);state.importItem(run,second);verify(mapper).bind(9L,30L,4L);
    }
    @Test void importRejectsExistingOtherProjectAndDetachedConnection() {
        repository(20L);
        assertThatThrownBy(()->state.importItem(run,item(30L))).isInstanceOf(IntegrationConflictException.class);
        repository(null).setConnectionId(null);
        assertThatThrownBy(()->state.importItem(run,item(null))).hasMessageContaining("连接已变化");
    }
    @Test void failuresAndSnapshotsPreserveOldDataWhenUnavailable() {
        state.failedItem(run,item(null),"失败");assertThat(run.getFailed()).isEqualTo(1);
        state.repositoryError(run,4L,"失败");verify(mapper).repositoryError(9L,4L,"失败");
        DevelopmentSnapshot snapshot=new DevelopmentSnapshot();snapshot.setKind("ISSUE");snapshot.setAvailable(false);
        state.snapshot(run,repository(20L),snapshot,List.of());
        verify(mapper,never()).clearDevelopment(anyLong(),anyLong(),anyString());
        snapshot.setAvailable(true);DevelopmentItem i=new DevelopmentItem();i.setKind("ISSUE");i.setTitle("x".repeat(1001));
        state.snapshot(run,repository(20L),snapshot,List.of(i));
        verify(mapper).clearDevelopment(9L,4L,"ISSUE");assertThat(i.getTitle()).hasSize(1000);assertThat(i.getWorkspaceId()).isEqualTo(9L);
    }
    @Test void finishUpdatesSuccessAndCancelsChangedConfigurations() {
        state.finish(run,"SUCCEEDED",null);assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        verify(mapper).completed(eq(9L),eq(2L),any(),isNull());
        run.setStatus("RUNNING");c.setVersion(2);state.finish(run,"SUCCEEDED",null);
        assertThat(run.getStatus()).isEqualTo("CANCELLED");
    }
    @Test void finishIgnoresExpiredLeaseAndMissingConnections() {
        when(mapper.lockRun(run)).thenReturn(null);state.finish(run,"FAILED","失败");
        verify(mapper,never()).completed(anyLong(),anyLong(),any(),any());
    }
    @Test void bindRequiresProjectWriteAndSharingAndRejectsConflicts() {
        repository(null);var result=state.bind(9L,20L,new RepositoryBindRequest(4L,false));
        assertThat(result.getProjectId()).isEqualTo(20L);
        verify(authorization).require(9L,WorkspacePermission.PROJECT_WRITE);
        repository(30L);
        assertThatThrownBy(()->state.bind(9L,20L,new RepositoryBindRequest(4L,false))).isInstanceOf(IntegrationConflictException.class);
        state.unbind(9L,20L,4L);verify(mapper).unbind(9L,20L,4L);
    }
}
