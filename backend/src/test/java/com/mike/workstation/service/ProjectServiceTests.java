package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.dto.ProjectRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private NoteMapper noteMapper;
    @Mock
    private NoteTagLoader noteTagLoader;
    @InjectMocks
    private ProjectService projectService;

    @Test
    void deleteRejectsProjectWithTasks() {
        when(projectMapper.findById(1L)).thenReturn(project());
        when(taskMapper.countByProjectId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在任务");
    }

    @Test
    void deleteClearsNoteProjectLinksBeforeProjectDelete() {
        when(projectMapper.findById(1L)).thenReturn(project());
        when(taskMapper.countByProjectId(1L)).thenReturn(0L);

        projectService.delete(1L);

        verify(noteMapper).clearProject(eq(1L), any(LocalDateTime.class));
        verify(projectMapper).delete(1L);
    }

    @Test
    void createRejectsRepositoryUrlLongerThanDatabaseColumn() {
        ProjectRequest request = new ProjectRequest(
                "Workstation", null, ProjectStatus.ACTIVE, "x".repeat(501), null, null
        );

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500");
    }

    @Test
    void listUsesStablePageOffsetAndTotal() {
        when(projectMapper.listPage(null, ProjectStatus.ACTIVE, 40L, 20)).thenReturn(java.util.List.of(project()));
        when(projectMapper.count(null, ProjectStatus.ACTIVE)).thenReturn(41L);

        var result = projectService.list(null, ProjectStatus.ACTIVE, 2, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(41L);
        assertThat(result.hasMore()).isFalse();
    }

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Workstation");
        project.setStatus(ProjectStatus.ACTIVE);
        return project;
    }
}
