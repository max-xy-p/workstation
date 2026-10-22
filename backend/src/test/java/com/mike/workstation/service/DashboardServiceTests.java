package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mike.workstation.config.DashboardProperties;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {
    private static final long WORKSPACE_ID = 9L;
    @Mock ProjectMapper projectMapper;
    @Mock TaskMapper taskMapper;
    @Mock NoteMapper noteMapper;
    @Mock NoteTagLoader noteTagLoader;
    @Mock WorkspaceAuthorizationService authorization;

    @Test
    void summaryAggregatesCountsAndLoadsRecentNoteTags() {
        Project project = new Project();
        project.setId(1L);
        project.setName("项目");
        Note note = new Note();
        note.setId(2L);
        note.setTitle("笔记");
        when(projectMapper.countByStatus(WORKSPACE_ID, ProjectStatus.ACTIVE)).thenReturn(4L);
        when(taskMapper.countByStatus(WORKSPACE_ID, TaskStatus.TODO)).thenReturn(3L);
        when(taskMapper.countByStatus(WORKSPACE_ID, TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(projectMapper.recent(WORKSPACE_ID, 5)).thenReturn(List.of(project));
        when(noteMapper.recentSummaries(WORKSPACE_ID, 5)).thenReturn(List.of(note));

        var response = service(new DashboardProperties(5)).summary(WORKSPACE_ID);

        assertThat(response.activeProjectCount()).isEqualTo(4L);
        assertThat(response.todoTaskCount()).isEqualTo(3L);
        assertThat(response.inProgressTaskCount()).isEqualTo(2L);
        assertThat(response.recentProjects()).hasSize(1);
        assertThat(response.recentNotes()).hasSize(1);
        verify(noteTagLoader).attachTags(WORKSPACE_ID, List.of(note));
    }

    @Test
    void invalidRecentLimitStopsBeforeReadingBusinessData() {
        assertThatThrownBy(() -> service(new DashboardProperties(0)).summary(WORKSPACE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未正确配置");
        verifyNoInteractions(projectMapper, taskMapper, noteMapper);
    }

    private DashboardService service(DashboardProperties properties) {
        return new DashboardService(
                projectMapper, taskMapper, noteMapper, noteTagLoader, properties, authorization);
    }
}
