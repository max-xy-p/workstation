package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.integration.*;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.exception.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationServiceTests {
    private final IntegrationMapper mapper=mock(IntegrationMapper.class);
    private final IntegrationStateService state=mock(IntegrationStateService.class);
    private final WorkspaceAuthorizationService auth=mock(WorkspaceAuthorizationService.class);
    private final RemoteApi remote=mock(RemoteApi.class);
    private final RepositoryProvider github=mock(RepositoryProvider.class);
    private final CredentialCipher cipher=mock(CredentialCipher.class);
    private final IntegrationProperties properties=new IntegrationProperties(true,"v1",Map.of(),List.of(),15,10,100,180,30);
    private final IntegrationService service=new IntegrationService(mapper,state,auth,properties,remote,List.of(github),cipher);
    private IntegrationConnection c;
    @BeforeEach void setup() {
        c=new IntegrationConnection();c.setId(2L);c.setWorkspaceId(9L);c.setProvider("GITHUB");c.setBaseUrl("https://github.com");
        c.setScope("OWNED");c.setAccount("account");c.setVersion(1);c.setCredential("encrypted");c.setKeyVersion("v1");c.setOwnerSubject("owner");
        when(mapper.connection(9L,2L)).thenReturn(c);
        when(github.provider()).thenReturn("GITHUB");when(github.account(any(),eq("token"))).thenReturn("account");
        when(remote.normalize(IntegrationProvider.GITHUB,"https://github.com")).thenReturn("https://github.com");
        when(cipher.decrypt(anyString(),eq("v1"),anyString())).thenReturn("token");
        when(state.saveConnection(anyLong(),any(),any(),any(),anyString())).thenReturn(c);
    }
    private IntegrationConnectionRequest request(String token,Integer version) {
        return new IntegrationConnectionRequest("name",IntegrationProvider.GITHUB,"https://github.com",token,IntegrationScope.OWNED,false,false,false,0,version,false);
    }
    @Test void createValidatesIdentityThenPersistsWithoutExposingCiphertext() {
        var response=service.save(9L,null,request("token",null));
        assertThat(response.account()).isEqualTo("account");assertThat(response.toString()).doesNotContain("encrypted","token");
        verify(github).account(any(),eq("token"));verify(state).saveConnection(eq(9L),isNull(),any(),any(),eq("token"));
    }
    @Test void updateDecryptsUnchangedCredentialAndRejectsConcurrentVersion() {
        service.save(9L,2L,request(null,1));verify(cipher).decrypt("encrypted","v1",IntegrationService.context(c));
        assertThatThrownBy(()->service.save(9L,2L,request(null,0))).isInstanceOf(IntegrationConflictException.class);
        assertThatThrownBy(()->service.save(9L,null,request("",null))).hasMessageContaining("凭证不能为空");
    }
    @Test void validationDoesNotSilentlySwitchAccount() {
        assertThat(service.validate(9L,2L).account()).isEqualTo("account");
        when(github.account(c,"token")).thenReturn("other");
        assertThatThrownBy(()->service.validate(9L,2L)).hasMessageContaining("账号已变化");
        assertThatThrownBy(()->service.require(10L,2L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(()->service.provider("UNKNOWN")).isInstanceOf(BusinessException.class);
        IntegrationService disabled=new IntegrationService(mapper,state,auth,new IntegrationProperties(false,"v1",Map.of(),List.of(),15,10,100,180,30),remote,List.of(github),cipher);
        assertThatThrownBy(disabled::checkEnabled).hasMessageContaining("未启用");
    }
    @Test void connectionManagementAndRepositoryReadsUseDistinctPermissions() {
        when(mapper.connections(9L,0,20)).thenReturn(List.of(c));when(mapper.countConnections(9L)).thenReturn(1L);
        assertThat(service.list(9L,0,20).items()).hasSize(1);
        ExternalRepository r=new ExternalRepository();r.setId(4L);r.setProvider("GITHUB");r.setName("repo");
        when(mapper.repositories(anyLong(),any(),any(),anyLong(),anyInt())).thenReturn(List.of(r));
        assertThat(service.repositories(9L,2L,null,0,20).items()).hasSize(1);
        service.repositories(9L,null,20L,0,20);verify(auth).require(9L,WorkspacePermission.PROJECT_READ);
        verify(state).requireProject(9L,20L);
        assertThatThrownBy(()->service.list(9L,0,101)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void runAccessIsBoundToConnectionAndWorkspaceAndPaged() {
        IntegrationRun run=new IntegrationRun();run.setId(3L);run.setStatus("QUEUED");run.setKind("DISCOVER");
        when(state.enqueue(9L,2L,"DISCOVER","key",null)).thenReturn(run);
        assertThat(service.trigger(9L,2L,"DISCOVER","key",null).id()).isEqualTo(3L);
        when(mapper.runs(9L,2L,0,20)).thenReturn(List.of(run));
        assertThat(service.runs(9L,2L,0,20).items()).hasSize(1);
        assertThatThrownBy(()->service.run(9L,2L,3L)).isInstanceOf(NotFoundException.class);
        when(mapper.run(9L,2L,3L)).thenReturn(run);
        assertThat(service.run(9L,2L,3L).status()).isEqualTo("QUEUED");
        IntegrationRunItem i=new IntegrationRunItem();i.setId(1L);i.setProjectStatus("PLANNING");
        when(mapper.items(9L,3L,0,20)).thenReturn(List.of(i));
        assertThat(service.items(9L,2L,3L,0,20).items()).hasSize(1);
    }
    @Test void repositoryBindingDeletionAndEnableDelegateToTransactionalState() {
        ExternalRepository r=new ExternalRepository();r.setProvider("GITHUB");r.setId(4L);
        var bind=new RepositoryBindRequest(4L,false);when(state.bind(9L,20L,bind)).thenReturn(r);
        assertThat(service.bind(9L,20L,bind).id()).isEqualTo(4L);
        var enable=new IntegrationEnableRequest(true,1,false);when(state.enabled(9L,2L,enable)).thenReturn(c);
        service.enabled(9L,2L,enable);service.unbind(9L,20L,4L);service.delete(9L,2L);
        verify(state).delete(9L,2L);verify(state).unbind(9L,20L,4L);
    }
    @Test void projectDevelopmentReturnsAllRepositoriesAndSnapshotMetadata() {
        ExternalRepository r=new ExternalRepository();r.setId(4L);r.setProvider("GITHUB");
        when(mapper.repositories(9L,null,20L,0,100)).thenReturn(Collections.nCopies(100,r));
        when(mapper.repositories(9L,null,20L,100,100)).thenReturn(List.of(r));
        DevelopmentSnapshot s=new DevelopmentSnapshot();s.setKind("ISSUE");s.setAvailable(false);s.setMessage("无权限");
        when(mapper.snapshots(9L,20L)).thenReturn(List.of(s));
        assertThat(service.development(9L,20L).repositories()).hasSize(101);
        assertThat(service.development(9L,20L).snapshots().get(0).available()).isFalse();
        DevelopmentItem i=new DevelopmentItem();i.setKind("ISSUE");
        when(mapper.development(9L,20L,"ISSUE",0,20)).thenReturn(List.of(i));
        assertThat(service.developmentItems(9L,20L,DevelopmentKind.ISSUE,0,20).items()).hasSize(1);
    }
}
