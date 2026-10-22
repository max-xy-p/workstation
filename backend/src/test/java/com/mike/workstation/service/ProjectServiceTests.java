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
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.dto.ProjectRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.mapper.IntegrationMapper;
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
class ProjectServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock private ProjectMapper projectMapper;
    @Mock private IntegrationMapper integrationMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private NoteMapper noteMapper;
    @Mock private NoteTagLoader noteTagLoader;
    @Mock private WorkspaceAuthorizationService authorization;
    @Mock private AuditService auditService;
    @InjectMocks private ProjectService projectService;

    @Test
    void deleteRejectsProjectWithTasks() {
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(project());
        when(taskMapper.countByProjectId(WORKSPACE_ID, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> projectService.delete(WORKSPACE_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在任务");
    }

    @Test
    void deleteClearsNoteProjectLinksBeforeProjectDelete() {
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(project());
        when(taskMapper.countByProjectId(WORKSPACE_ID, 1L)).thenReturn(0L);

        projectService.delete(WORKSPACE_ID, 1L);

        verify(noteMapper).clearProject(eq(WORKSPACE_ID), eq(1L), any(LocalDateTime.class));
        verify(projectMapper).delete(WORKSPACE_ID, 1L);
    }

    @Test
    void createRejectsRepositoryUrlLongerThanDatabaseColumn() {
        ProjectRequest request =
                new ProjectRequest(
                        "Workstation",
                        null,
                        ProjectStatus.ACTIVE,
                        "x".repeat(501),
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> projectService.create(WORKSPACE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500");
    }

    @Test
    void listUsesStablePageOffsetAndTotal() {
        when(projectMapper.listPage(WORKSPACE_ID, null, ProjectStatus.ACTIVE, 40L, 20))
                .thenReturn(List.of(project()));
        when(projectMapper.count(WORKSPACE_ID, null, ProjectStatus.ACTIVE)).thenReturn(41L);

        var result = projectService.list(WORKSPACE_ID, null, ProjectStatus.ACTIVE, 2, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(41L);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void detailLoadsTasksAndNotesOnlyAfterProjectExists() {
        Project project = project();
        Note note = new Note();
        note.setId(3L);
        when(projectMapper.findById(WORKSPACE_ID, 1L)).thenReturn(project);
        when(noteMapper.listSummariesByProjectId(WORKSPACE_ID, 1L)).thenReturn(List.of(note));
        when(taskMapper.listByProjectId(WORKSPACE_ID, 1L)).thenReturn(List.of());

        var detail = projectService.detail(WORKSPACE_ID, 1L);

        assertThat(detail.notes()).hasSize(1);
        verify(noteTagLoader).attachTags(WORKSPACE_ID, List.of(note));
        verify(taskMapper).listByProjectId(WORKSPACE_ID, 1L);
    }

    @Test
    void missingProjectDoesNotLoadAssociatedData() {
        assertThatThrownBy(() -> projectService.detail(WORKSPACE_ID, 99L))
                .isInstanceOf(NotFoundException.class);
        verify(noteMapper, never()).listSummariesByProjectId(WORKSPACE_ID, 99L);
        verify(taskMapper, never()).listByProjectId(WORKSPACE_ID, 99L);
    }

    @Test
    void createTrimsNameAndAuditsPersistedProject() {
        Mockito.doAnswer(
                        invocation -> {
                            Project inserted = invocation.getArgument(0);
                            inserted.setId(7L);
                            return 1;
                        })
                .when(projectMapper)
                .insert(any(Project.class));
        Project persisted = project();
        persisted.setId(7L);
        persisted.setName("Workstation");
        when(projectMapper.findById(WORKSPACE_ID, 7L)).thenReturn(persisted);

        var result =
                projectService.create(
                        WORKSPACE_ID,
                        new ProjectRequest(
                                "  Workstation  ",
                                "描述",
                                ProjectStatus.ACTIVE,
                                null,
                                null,
                                null,
                                null));

        assertThat(result.id()).isEqualTo(7L);
        var captor = ArgumentCaptor.forClass(Project.class);
        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Workstation");
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        verify(auditService).success(WORKSPACE_ID, "PROJECT_CREATE", "PROJECT", 7L);
    }

    @Test
    void statusChangeRequiresExistingProject() {
        assertThatThrownBy(
                        () -> projectService.updateStatus(WORKSPACE_ID, 99L, ProjectStatus.ACTIVE))
                .isInstanceOf(NotFoundException.class);
        verify(projectMapper, never())
                .updateStatus(
                        eq(WORKSPACE_ID),
                        eq(99L),
                        eq(ProjectStatus.ACTIVE),
                        any(LocalDateTime.class));
    }

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Workstation");
        project.setStatus(ProjectStatus.ACTIVE);
        return project;
    }
}
