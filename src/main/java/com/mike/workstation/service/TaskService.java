package com.mike.workstation.service;

import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;

    public TaskService(TaskMapper taskMapper, ProjectMapper projectMapper) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
    }

    public List<Task> list(String keyword, Long projectId, TaskStatus status, TaskPriority priority) {
        return taskMapper.list(keyword, projectId, status, priority);
    }

    public Task get(Long id) {
        Task task = taskMapper.findById(id);
        RequestChecks.requiredExisting(task, "任务不存在");
        return task;
    }

    @Transactional
    public Task create(TaskRequest request) {
        validate(request);
        Task task = new Task();
        applyRequest(task, request);
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return get(task.getId());
    }

    @Transactional
    public Task update(Long id, TaskRequest request) {
        validate(request);
        get(id);
        Task task = new Task();
        task.setId(id);
        applyRequest(task, request);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.update(task);
        return get(id);
    }

    @Transactional
    public Task updateStatus(Long id, TaskStatus status) {
        RequestChecks.requiredObject(status, "任务状态");
        get(id);
        taskMapper.updateStatus(id, status, LocalDateTime.now());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        taskMapper.delete(id);
    }

    private void validate(TaskRequest request) {
        RequestChecks.requiredObject(request, "任务请求");
        RequestChecks.requiredText(request.title(), "任务标题");
        RequestChecks.requiredObject(request.projectId(), "所属项目");
        RequestChecks.requiredObject(request.status(), "任务状态");
        RequestChecks.requiredObject(request.priority(), "任务优先级");
        RequestChecks.requiredExisting(projectMapper.findById(request.projectId()), "所属项目不存在");
    }

    private void applyRequest(Task task, TaskRequest request) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setProjectId(request.projectId());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
    }
}
