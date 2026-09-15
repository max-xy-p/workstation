package com.mike.workstation.service;

import com.mike.workstation.assembler.TaskResponseAssembler;
import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.dto.TaskResponse;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 任务业务服务。
 *
 * <p>负责任务的必填校验、所属项目校验，以及看板状态流转。</p>
 */
public class TaskService {
    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;

    public TaskService(TaskMapper taskMapper, ProjectMapper projectMapper) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
    }

    /**
     * 查询任务列表。
     *
     * @param keyword 标题关键字，可为空
     * @param projectId 项目筛选，可为空
     * @param status 状态筛选，可为空
     * @param priority 优先级筛选，可为空
     * @return 任务列表
     */
    public List<TaskResponse> list(String keyword, Long projectId, TaskStatus status, TaskPriority priority) {
        return taskMapper.list(keyword, projectId, status, priority).stream()
                .map(TaskResponseAssembler::toResponse)
                .toList();
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     */
    public TaskResponse get(Long id) {
        return TaskResponseAssembler.toResponse(getEntity(id));
    }

    private Task getEntity(Long id) {
        Task task = taskMapper.findById(id);
        RequestChecks.requiredExisting(task, "任务不存在");
        return task;
    }

    /**
     * 创建任务。
     *
     * @param request 任务创建请求
     * @return 创建后的任务
     */
    @Transactional
    public TaskResponse create(TaskRequest request) {
        validate(request);
        Task task = new Task();
        applyRequest(task, request);
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return get(task.getId());
    }

    /**
     * 更新任务完整信息。
     *
     * @param id 任务 ID
     * @param request 任务更新请求
     * @return 更新后的任务
     */
    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        validate(request);
        getEntity(id);
        Task task = new Task();
        task.setId(id);
        applyRequest(task, request);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.update(task);
        return get(id);
    }

    /**
     * 更新任务看板状态。
     *
     * @param id 任务 ID
     * @param status 新状态
     * @return 更新后的任务
     */
    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus status) {
        RequestChecks.requiredObject(status, "任务状态");
        getEntity(id);
        taskMapper.updateStatus(id, status, LocalDateTime.now());
        return get(id);
    }

    /**
     * 删除任务。
     *
     * @param id 任务 ID
     */
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        taskMapper.delete(id);
    }

    /**
     * 校验任务请求。
     *
     * <p>所属项目必须存在；状态和优先级由前端显式选择，不在后端填默认值。</p>
     */
    private void validate(TaskRequest request) {
        RequestChecks.requiredObject(request, "任务请求");
        RequestChecks.requiredText(request.title(), "任务标题");
        RequestChecks.requiredObject(request.projectId(), "所属项目");
        RequestChecks.requiredObject(request.status(), "任务状态");
        RequestChecks.requiredObject(request.priority(), "任务优先级");
        RequestChecks.maxLength(request.title().trim(), 200, "任务标题");
        RequestChecks.requiredExisting(projectMapper.findById(request.projectId()), "所属项目不存在");
    }

    /**
     * 把请求对象复制到任务实体。
     */
    private void applyRequest(Task task, TaskRequest request) {
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setProjectId(request.projectId());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
    }
}
