package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.domain.Task;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;
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
    private WorkspaceAuthorizationService authorization;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private TaskService taskService;

    @Test
    void createRejectsTitleLongerThanDatabaseColumn() {
        TaskRequest request = new TaskRequest(
                "x".repeat(201), null, 1L, TaskStatus.TODO, TaskPriority.MEDIUM, null
        );

        assertThatThrownBy(() -> taskService.create(WORKSPACE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
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
}
