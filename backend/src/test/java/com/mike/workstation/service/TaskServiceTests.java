package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.TaskMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private NoteMapper noteMapper;
    @Mock
    private WorkspaceAuthorizationService authorization;
    @Mock
    private AuditService auditService;
    @Mock
    private com.mike.workstation.mapper.IssueSourceMapper issueSources;
    @InjectMocks
    private TaskService taskService;

    @Test
    void createRejectsTitleLongerThanDatabaseColumn() {
        TaskRequest request = new TaskRequest(
                "x".repeat(201), null, 1L, TaskStatus.TODO, TaskPriority.MEDIUM, null, null
        );

        assertThatThrownBy(() -> taskService.create(WORKSPACE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    @Test
    void evidenceNoteMustBelongToTaskProject() {
        Project project = new Project();
        project.setId(1L);
        Note note = new Note();
        note.setId(7L);
        note.setProjectId(2L);
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(project);
        when(noteMapper.lockById(WORKSPACE_ID, 7L)).thenReturn(note);

        var request = new TaskRequest(
                "Task", null, 1L, TaskStatus.TODO, TaskPriority.MEDIUM, null, 7L);
        assertThatThrownBy(() -> taskService.create(WORKSPACE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("依据笔记");
    }

    @Test
    void listRejectsPageSizeAboveMaximum() {
        assertThatThrownBy(() -> taskService.list(WORKSPACE_ID, null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void listReturnsRequestedPage() {
        Task task = new Task();
        task.setId(1L);
        when(taskMapper.listPage(WORKSPACE_ID, null, null, null, null, 10L, 10))
                .thenReturn(java.util.List.of(task));
        when(taskMapper.count(WORKSPACE_ID, null, null, null, null)).thenReturn(21L);

        var result = taskService.list(WORKSPACE_ID, null, null, null, null, 1, 10);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void createWithMatchingEvidencePersistsLinkAndAuditsTask() {
        Project project = new Project();
        project.setId(1L);
        Note note = new Note();
        note.setId(7L);
        note.setProjectId(1L);
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(project);
        when(noteMapper.lockById(WORKSPACE_ID, 7L)).thenReturn(note);
        org.mockito.Mockito.doAnswer(invocation -> {
            Task inserted = invocation.getArgument(0);
            inserted.setId(11L);
            return 1;
        }).when(taskMapper).insert(any(Task.class));
        Task persisted = new Task();
        persisted.setId(11L);
        when(taskMapper.findById(WORKSPACE_ID, 11L)).thenReturn(persisted);

        var result = taskService.create(WORKSPACE_ID,
                new TaskRequest("  Task  ", null, 1L, TaskStatus.TODO,
                        TaskPriority.MEDIUM, null, 7L));

        assertThat(result.id()).isEqualTo(11L);
        var captor = org.mockito.ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Task");
        assertThat(captor.getValue().getEvidenceNoteId()).isEqualTo(7L);
        verify(auditService).success(WORKSPACE_ID, "TASK_CREATE", "TASK", 11L);
    }

    @Test
    void missingTaskCannotChangeStatusOrBeDeleted() {
        assertThatThrownBy(() -> taskService.updateStatus(WORKSPACE_ID, 99L, TaskStatus.DONE))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> taskService.delete(WORKSPACE_ID, 99L))
                .isInstanceOf(NotFoundException.class);
        verify(taskMapper, never()).updateStatus(eq(WORKSPACE_ID), eq(99L),
                eq(TaskStatus.DONE), any(LocalDateTime.class));
        verify(taskMapper, never()).delete(WORKSPACE_ID, 99L);
    }

    @Test
    void deleteExistingTaskWritesAuditRecord() {
        Task persisted = new Task();
        persisted.setId(4L);
        when(taskMapper.findById(WORKSPACE_ID, 4L)).thenReturn(persisted);

        taskService.delete(WORKSPACE_ID, 4L);

        verify(taskMapper).delete(WORKSPACE_ID, 4L);
        verify(auditService).success(WORKSPACE_ID, "TASK_DELETE", "TASK", 4L);
    }

    @Test void movingLinkedTaskRequiresUnlink() {
        Project project=new Project();when(projectMapper.findById(WORKSPACE_ID,2L)).thenReturn(project);
        Task task=new Task();when(taskMapper.findById(WORKSPACE_ID,4L)).thenReturn(task);when(issueSources.linkedProject(WORKSPACE_ID,4L)).thenReturn(1L);
        var request=new TaskRequest("任务",null,2L,TaskStatus.TODO,TaskPriority.MEDIUM,null,null);
        assertThatThrownBy(()->taskService.update(WORKSPACE_ID,4L,request)).hasMessageContaining("解除");verify(taskMapper,never()).update(any());
    }
    @Test void lockedTaskReadUsesLatestRow() {
        Task task=new Task();task.setId(4L);when(taskMapper.lockById(WORKSPACE_ID,4L)).thenReturn(task);assertThat(taskService.getLocked(WORKSPACE_ID,4L).id()).isEqualTo(4L);
        assertThatThrownBy(()->taskService.getLocked(WORKSPACE_ID,5L)).isInstanceOf(NotFoundException.class);
    }
}
