package com.mike.workstation.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.assembler.TaskResponseAssembler;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.exception.*;
import com.mike.workstation.mapper.*;
import com.mike.workstation.security.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;

class TaskIssueServiceTests {
    private final IssueSourceMapper sources=mock(IssueSourceMapper.class);
    private final IntegrationMapper integrations=mock(IntegrationMapper.class);
    private final TaskService tasks=mock(TaskService.class);
    private final WorkspaceAuthorizationService auth=mock(WorkspaceAuthorizationService.class);
    private final CurrentPrincipalResolver principals=mock(CurrentPrincipalResolver.class);
    private final AuditService audit=mock(AuditService.class);
    private final TaskIssueService service=new TaskIssueService(sources,integrations,tasks,auth,principals,audit);
    private DevelopmentItem item;
    private ExternalRepository repo;
    private IssueSource source;
    private Workspace workspace;
    @BeforeEach void setup() {
        workspace=new Workspace();workspace.setType(WorkspaceType.SHARED);when(auth.require(eq(9L),any())).thenReturn(workspace);
        when(principals.require()).thenReturn(new CurrentPrincipal("author",PrincipalType.USER,"author",null,Set.of()));
        item=new DevelopmentItem();item.setId(1L);item.setRepositoryId(2L);item.setKind("ISSUE");item.setExternalId("100");
        item.setIssueNumber(3L);item.setTitle("来源");item.setStatus("OPEN");when(sources.candidate(9L,1L)).thenReturn(item);
        repo=new ExternalRepository();repo.setId(2L);repo.setProjectId(20L);repo.setProvider("GITHUB");repo.setBaseUrl("https://github.com");
        when(integrations.repository(9L,2L)).thenReturn(repo);when(sources.boundProject(9L,2L)).thenReturn(20L);
        when(sources.linkedTask(anyLong(),anyLong())).thenReturn(null);when(sources.sourceForTask(anyLong(),anyLong())).thenReturn(null);
        source=new IssueSource();source.setId(4L);source.setRepositoryId(2L);when(sources.identity(any())).thenReturn(source);
        Task entity=new Task();entity.setId(5L);entity.setProjectId(20L);entity.setTitle("本地");
        TaskResponse task=TaskResponseAssembler.toResponse(entity);when(tasks.create(eq(9L),any())).thenReturn(task);when(tasks.getLocked(9L,5L)).thenReturn(task);
    }
    private TaskRequest task(Long project) { return new TaskRequest("本地",null,project,TaskStatus.TODO,TaskPriority.HIGH,null,null); }
    private IssueTaskCreateRequest request() { return new IssueTaskCreateRequest(1L,true,task(20L)); }
    @Test void createsTaskAndLinkAndAudit() {
        assertThat(service.create(9L,20L,request()).created()).isTrue();verify(sources).link(9L,5L,4L,"author");verify(audit).success(9L,"TASK_ISSUE_LINK","TASK",5L);
    }
    @Test void duplicateReturnsExistingWithoutOverwritingTask() {
        when(sources.linkedTask(9L,4L)).thenReturn(5L);assertThat(service.create(9L,20L,request()).created()).isFalse();verify(tasks,never()).create(anyLong(),any());
    }
    @Test void existingTaskLinkIsIdempotent() {
        var req=new IssueTaskLinkRequest(1L,true,5L);service.link(9L,20L,req);
        when(sources.linkedTask(9L,4L)).thenReturn(5L);when(sources.sourceForTask(9L,5L)).thenReturn(4L);service.link(9L,20L,req);
        verify(sources,times(1)).link(9L,5L,4L,"author");
    }
    @Test void rejectsIssueAndTaskAssociationConflicts() {
        when(sources.linkedTask(9L,4L)).thenReturn(6L);assertThatThrownBy(()->service.link(9L,20L,new IssueTaskLinkRequest(1L,true,5L))).isInstanceOf(TaskIssueConflictException.class);
        when(sources.linkedTask(9L,4L)).thenReturn(null);when(sources.sourceForTask(9L,5L)).thenReturn(8L);
        assertThatThrownBy(()->service.link(9L,20L,new IssueTaskLinkRequest(1L,true,5L))).isInstanceOf(TaskIssueConflictException.class);
    }
    @Test void sharedWorkspaceRequiresConfirmationBeforeWrite() {
        assertThatThrownBy(()->service.create(9L,20L,new IssueTaskCreateRequest(1L,false,task(20L)))).isInstanceOf(BusinessException.class);verify(sources,never()).lockWorkspace(anyLong());
        workspace.setType(WorkspaceType.PERSONAL);assertThat(service.create(9L,20L,new IssueTaskCreateRequest(1L,null,task(20L))).created()).isTrue();
    }
    @Test void rejectsMissingSnapshotAndPullRequest() {
        when(sources.candidate(9L,1L)).thenReturn(null);assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(NotFoundException.class);
        when(sources.candidate(9L,1L)).thenReturn(item);item.setKind("CHANGE_REQUEST");assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsWrongPlatformProjectAndMissingNumber() {
        repo.setProvider("GITLAB");assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(BusinessException.class);
        repo.setProvider("GITHUB");when(sources.boundProject(9L,2L)).thenReturn(21L);assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(BusinessException.class);
        when(sources.boundProject(9L,2L)).thenReturn(20L);item.setIssueNumber(null);assertThatThrownBy(()->service.create(9L,20L,request())).hasMessageContaining("编号");
        item.setIssueNumber(0L);assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsTransferredIdentityAndDifferentTaskProject() {
        source.setRepositoryId(7L);assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(TaskIssueConflictException.class);
        source.setRepositoryId(2L);assertThatThrownBy(()->service.create(9L,20L,new IssueTaskCreateRequest(1L,true,task(21L)))).hasMessageContaining("来源项目");
        Task other=new Task();other.setId(5L);other.setProjectId(21L);when(tasks.getLocked(9L,5L)).thenReturn(TaskResponseAssembler.toResponse(other));
        assertThatThrownBy(()->service.link(9L,20L,new IssueTaskLinkRequest(1L,true,5L))).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsMissingInputsAndRepository() {
        assertThatThrownBy(()->service.create(9L,20L,new IssueTaskCreateRequest(null,true,task(20L)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.link(9L,20L,new IssueTaskLinkRequest(1L,true,null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create(9L,20L,new IssueTaskCreateRequest(1L,true,null))).isInstanceOf(BusinessException.class);
        when(integrations.repository(9L,2L)).thenReturn(null);assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(NotFoundException.class);
    }
    @Test void authorizationPrecedesAnySourceAccess() {
        when(auth.require(9L,WorkspacePermission.TASK_WRITE)).thenThrow(new WorkspaceAccessDeniedException("无权限"));
        assertThatThrownBy(()->service.create(9L,20L,request())).isInstanceOf(WorkspaceAccessDeniedException.class);verifyNoInteractions(sources);
    }
    @Test void unlinkAuditsOnlyAnActualRemoval() {
        when(sources.unlink(9L,5L)).thenReturn(1,0);service.unlink(9L,5L);service.unlink(9L,5L);verify(audit,times(1)).success(9L,"TASK_ISSUE_UNLINK","TASK",5L);
    }
    @Test void listUsesBatchedPagination() {
        source.setTaskId(5L);when(sources.list(9L,20L,0,20)).thenReturn(List.of(source));when(sources.count(9L,20L)).thenReturn(1L);
        assertThat(service.list(9L,20L,0,20).items()).singleElement().satisfies(s->assertThat(s.taskId()).isEqualTo(5L));
        assertThatThrownBy(()->service.list(9L,null,0,101)).isInstanceOf(IllegalArgumentException.class);
    }
}
