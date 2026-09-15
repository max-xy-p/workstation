package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
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
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @InjectMocks
    private TaskService taskService;

    @Test
    void createRejectsTitleLongerThanDatabaseColumn() {
        TaskRequest request = new TaskRequest(
                "x".repeat(201), null, 1L, TaskStatus.TODO, TaskPriority.MEDIUM, null
        );

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }
}
