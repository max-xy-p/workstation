package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.TaskVersionConflictException;
import com.mike.workstation.mapper.IssueSourceMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class TaskServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock private TaskMapper taskMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private NoteMapper noteMapper;
    @Mock private WorkspaceAuthorizationService authorization;
    @Mock private AuditService auditService;
    @Mock private IssueSourceMapper issueSources;
    @InjectMocks private TaskService taskService;

    @Test
    void createRejectsTitleLongerThanDatabaseColumn() {
        TaskRequest request =
                new TaskRequest(
                        "x".repeat(201),
                        null,
                        1L,
                        TaskStatus.TODO,
                        TaskPriority.MEDIUM,
                        null,
                        null,
                        0L);

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

        var request =
                new TaskRequest(
                        "Task", null, 1L, TaskStatus.TODO, TaskPriority.MEDIUM, null, 7L, 0L);
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
                .thenReturn(List.of(task));
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
        Mockito.doAnswer(
                        invocation -> {
                            Task inserted = invocation.getArgument(0);
                            inserted.setId(11L);
                            return 1;
                        })
                .when(taskMapper)
                .insert(any(Task.class));
        Task persisted = new Task();
        persisted.setId(11L);
        when(taskMapper.findById(WORKSPACE_ID, 11L)).thenReturn(persisted);

        var result =
                taskService.create(
                        WORKSPACE_ID,
                        new TaskRequest(
                                "  Task  ",
                                null,
                                1L,
                                TaskStatus.TODO,
                                TaskPriority.MEDIUM,
                                null,
                                7L,
                                0L));

        assertThat(result.id()).isEqualTo(11L);
        var captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Task");
        assertThat(captor.getValue().getEvidenceNoteId()).isEqualTo(7L);
        verify(auditService).success(WORKSPACE_ID, "TASK_CREATE", "TASK", 11L);
    }

    @Test
    void missingTaskCannotChangeStatusOrBeDeleted() {
        assertThatThrownBy(() -> taskService.updateStatus(WORKSPACE_ID, 99L, TaskStatus.DONE, 0L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> taskService.delete(WORKSPACE_ID, 99L, 0L))
                .isInstanceOf(NotFoundException.class);
        verify(taskMapper)
                .updateStatus(
                        eq(WORKSPACE_ID),
                        eq(99L),
                        eq(TaskStatus.DONE),
                        any(LocalDateTime.class),
                        eq(0L));
        verify(taskMapper).delete(WORKSPACE_ID, 99L, 0L);
    }

    @Test
    void deleteExistingTaskWritesAuditRecord() {
        Task persisted = new Task();
        persisted.setId(4L);
        when(taskMapper.delete(WORKSPACE_ID, 4L, 0L)).thenReturn(1);

        taskService.delete(WORKSPACE_ID, 4L, 0L);

        verify(taskMapper).delete(WORKSPACE_ID, 4L, 0L);
        verify(auditService).success(WORKSPACE_ID, "TASK_DELETE", "TASK", 4L);
    }

    @Test
    void movingLinkedTaskRequiresUnlink() {
        Project project = new Project();
        when(projectMapper.findById(WORKSPACE_ID, 2L)).thenReturn(project);
        Task task = new Task();
        when(taskMapper.findById(WORKSPACE_ID, 4L)).thenReturn(task);
        when(issueSources.linkedProject(WORKSPACE_ID, 4L)).thenReturn(1L);
        var request =
                new TaskRequest(
                        "任务", null, 2L, TaskStatus.TODO, TaskPriority.MEDIUM, null, null, 0L);
        assertThatThrownBy(() -> taskService.update(WORKSPACE_ID, 4L, request))
                .hasMessageContaining("解除");
        verify(taskMapper, never()).update(any());
    }

    @Test
    void lockedTaskReadUsesLatestRow() {
        Task task = new Task();
        task.setId(4L);
        when(taskMapper.lockById(WORKSPACE_ID, 4L)).thenReturn(task);
        assertThat(taskService.getLocked(WORKSPACE_ID, 4L).id()).isEqualTo(4L);
        assertThatThrownBy(() -> taskService.getLocked(WORKSPACE_ID, 5L))
                .isInstanceOf(NotFoundException.class);
    }

    private TaskRequest updateRequest(Long version) {
        return new TaskRequest(
                " 新标题 ", null, 1L, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, null, version);
    }

    private Task storedTask() {
        Task task = new Task();
        task.setId(4L);
        task.setVersion(8L);
        return task;
    }

    @Test
    void conditionalUpdateUsesClientVersionAndAuditsOnlySuccess() {
        when(issueSources.linkedProject(WORKSPACE_ID, 4L)).thenReturn(null);
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(new Project());
        when(taskMapper.findById(WORKSPACE_ID, 4L)).thenReturn(storedTask());
        when(taskMapper.update(any())).thenReturn(1);
        taskService.update(WORKSPACE_ID, 4L, updateRequest(7L));
        var captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).update(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(7L);
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        verify(auditService).success(WORKSPACE_ID, "TASK_UPDATE", "TASK", 4L);
    }

    @Test
    void staleUpdateCannotEmitSuccessAudit() {
        when(issueSources.linkedProject(WORKSPACE_ID, 4L)).thenReturn(null);
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(new Project());
        when(taskMapper.findById(WORKSPACE_ID, 4L)).thenReturn(storedTask());
        when(taskMapper.lockById(WORKSPACE_ID, 4L)).thenReturn(storedTask());
        assertThatThrownBy(() -> taskService.update(WORKSPACE_ID, 4L, updateRequest(7L)))
                .isInstanceOf(TaskVersionConflictException.class);
        Mockito.verifyNoInteractions(auditService);
    }

    @Test
    void missingAndNegativeVersionsAreRejectedBeforeTaskMutation() {
        for (Long version : new Long[] {null, -1L}) {
            assertThatThrownBy(() -> taskService.update(WORKSPACE_ID, 4L, updateRequest(version)))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(
                            () ->
                                    taskService.updateStatus(
                                            WORKSPACE_ID, 4L, TaskStatus.DONE, version))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> taskService.delete(WORKSPACE_ID, 4L, version))
                    .isInstanceOf(BusinessException.class);
        }
        Mockito.verifyNoInteractions(taskMapper, auditService);
    }

    @Test
    void staleStatusAndDeleteUseCurrentReadAndPreserveTask() {
        when(taskMapper.lockById(WORKSPACE_ID, 4L)).thenReturn(storedTask());
        assertThatThrownBy(() -> taskService.updateStatus(WORKSPACE_ID, 4L, TaskStatus.DONE, 7L))
                .isInstanceOf(TaskVersionConflictException.class);
        assertThatThrownBy(() -> taskService.delete(WORKSPACE_ID, 4L, 7L))
                .isInstanceOf(TaskVersionConflictException.class);
        Mockito.verifyNoInteractions(auditService);
    }

    @Test
    void statusSuccessRequiresExactlyOneAffectedRow() {
        when(taskMapper.updateStatus(eq(WORKSPACE_ID), eq(4L), eq(TaskStatus.DONE), any(), eq(7L)))
                .thenReturn(1);
        when(taskMapper.findById(WORKSPACE_ID, 4L)).thenReturn(storedTask());
        assertThat(taskService.updateStatus(WORKSPACE_ID, 4L, TaskStatus.DONE, 7L).version())
                .isEqualTo(8L);
        verify(auditService).success(WORKSPACE_ID, "TASK_STATUS_UPDATE", "TASK", 4L);
    }
}
