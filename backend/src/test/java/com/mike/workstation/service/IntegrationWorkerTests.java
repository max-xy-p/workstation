package com.mike.workstation.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.mike.workstation.domain.*;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.integration.*;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.exception.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationWorkerTests {
    private final IntegrationMapper mapper=mock(IntegrationMapper.class);
    private final IntegrationStateService state=mock(IntegrationStateService.class);
    private final IntegrationService service=mock(IntegrationService.class);
    private final CredentialCipher cipher=mock(CredentialCipher.class);
    private final RepositoryProvider provider=mock(RepositoryProvider.class);
    private final IntegrationProperties properties=new IntegrationProperties(true,"v1",Map.of(),List.of(),15,2,2,180,30);
    private final IntegrationWorker worker=new IntegrationWorker(mapper,state,service,cipher,properties);
    private IntegrationRun run;
    private IntegrationConnection c;
    @BeforeEach void setup() {
        run=new IntegrationRun();run.setId(3L);run.setWorkspaceId(9L);run.setConnectionId(2L);run.setKind("DISCOVER");
        c=new IntegrationConnection();c.setId(2L);c.setWorkspaceId(9L);c.setProvider("GITHUB");c.setCredential("cipher");c.setKeyVersion("v1");
        when(state.claim()).thenReturn(run);when(service.require(9L,2L)).thenReturn(c);
        when(service.provider("GITHUB")).thenReturn(provider);when(cipher.decrypt(anyString(),anyString(),anyString())).thenReturn("token");
    }
    private ExternalRepository repo() { ExternalRepository r=new ExternalRepository();r.setId(4L);return r; }
    @Test void processesAllDiscoveryPagesAndStopsOnShortPage() {
        when(provider.repositories(c,"token",1)).thenReturn(Collections.nCopies(100,repo()));
        when(provider.repositories(c,"token",2)).thenReturn(List.of(repo()));
        worker.execute();verify(state,times(2)).saveRepositories(eq(run),anyList());
        verify(state).finish(run,"SUCCEEDED",null);
    }
    @Test void discoveryBoundMarksIncompleteAndDoesNotPretendSuccess() {
        when(provider.repositories(eq(c),eq("token"),anyInt())).thenReturn(Collections.nCopies(100,repo()));
        worker.execute();verify(state).finish(eq(run),eq("FAILED"),contains("分页上限"));
    }
    @Test void noQueuedTaskDoesNothingAndScheduledTriggerUsesSameStateService() {
        when(state.claim()).thenReturn(null);worker.execute();verify(service,never()).require(anyLong(),anyLong());
        when(mapper.dueConnections()).thenReturn(List.of(c));worker.schedule();verify(state).schedule(9L,2L);
    }
    @Test void configChangeCancelsAndUnexpectedExceptionIsRedacted() {
        doThrow(new IntegrationConflictException("secret")).when(state).heartbeat(run);worker.execute();
        verify(state).finish(run,"CANCELLED","连接配置、权限或运行租约已变化");
        assertThat(IntegrationWorker.safeError(new IllegalStateException("token"))).doesNotContain("token");
        assertThat(IntegrationWorker.safeError(new BusinessException("平台暂时不可用"))).isEqualTo("平台暂时不可用");
    }
    @Test void importRecoverySkipsCommittedItemsAndRecordsPerItemFailure() {
        run.setKind("IMPORT");
        IntegrationRunItem done=new IntegrationRunItem();done.setStatus("SUCCEEDED");
        IntegrationRunItem pending=new IntegrationRunItem();pending.setStatus("QUEUED");
        when(mapper.items(9L,3L,0,100)).thenReturn(List.of(done,pending));
        doThrow(new BusinessException("目标不存在")).when(state).importItem(run,pending);
        worker.execute();verify(state,never()).importItem(run,done);
        verify(state).failedItem(run,pending,"目标不存在");
    }
    @Test void syncSeparatesResourceFailuresAndCapsHistory() {
        run.setKind("SYNC");ExternalRepository r=repo();
        when(mapper.repositories(9L,2L,-1L,0,100)).thenReturn(List.of(r));
        when(provider.repository(c,"token",r)).thenReturn(r);
        when(provider.development(eq(c),eq("token"),eq(r),any(),any(),eq(1))).thenAnswer(inv->{
            DevelopmentKind kind=inv.getArgument(3);
            if(kind==DevelopmentKind.ISSUE) throw new BusinessException("无 Issue 权限");
            List<DevelopmentItem> list=new ArrayList<>();
            for(int n=0;n<3;n++) {DevelopmentItem i=new DevelopmentItem();i.setKind(kind.name());i.setTitle("记录");i.setStatus("OPEN");list.add(i);}
            return list;
        });
        worker.execute();
        var captor=ArgumentCaptor.forClass(DevelopmentSnapshot.class);
        verify(state,times(4)).snapshot(eq(run),eq(r),captor.capture(),anyList());
        assertThat(captor.getAllValues()).filteredOn(s->s.getKind().equals("ISSUE")).singleElement().satisfies(s->assertThat(s.isAvailable()).isFalse());
        assertThat(captor.getAllValues()).filteredOn(DevelopmentSnapshot::isAvailable).allSatisfy(s->{
            assertThat(s.isTruncated()).isTrue();assertThat(s.getCollected()).isEqualTo(2);
        });
        verify(state).repositoryError(run,4L,"无 Issue 权限");
    }
    @Test void syncRetainsRepositoryWhenSourceUnavailable() {
        run.setKind("SYNC");ExternalRepository r=repo();
        when(mapper.repositories(9L,2L,-1L,0,100)).thenReturn(List.of(r));
        when(provider.repository(c,"token",r)).thenThrow(new BusinessException("仓库不可读"));
        worker.execute();verify(state).repositoryError(run,4L,"仓库不可读");
        verify(state,never()).refreshRepository(eq(run),any());
    }
    @Test void syncFiltersPastWindowAndPrPlaceholders() {
        run.setKind("SYNC");ExternalRepository r=repo();
        when(mapper.repositories(9L,2L,-1L,0,100)).thenReturn(List.of(r));when(provider.repository(c,"token",r)).thenReturn(r);
        DevelopmentItem old=new DevelopmentItem();old.setKind("ISSUE");old.setSourceUpdatedAt(LocalDateTime.now().minusYears(1));
        DevelopmentItem pull=new DevelopmentItem();pull.setKind("CHANGE_REQUEST");
        when(provider.development(eq(c),eq("token"),eq(r),eq(DevelopmentKind.ISSUE),any(),eq(1))).thenReturn(List.of(old,pull));
        worker.execute();
        var captor=ArgumentCaptor.forClass(DevelopmentSnapshot.class);verify(state,times(4)).snapshot(eq(run),eq(r),captor.capture(),anyList());
        assertThat(captor.getAllValues()).filteredOn(s->s.getKind().equals("ISSUE")).singleElement().satisfies(s->assertThat(s.getCollected()).isZero());
    }
}
