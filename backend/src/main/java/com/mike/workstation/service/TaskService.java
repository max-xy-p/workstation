package com.mike.workstation.service;

import com.mike.workstation.assembler.TaskResponseAssembler;
import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.dto.TaskResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final NoteMapper noteMapper;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;

    public TaskService(
            TaskMapper taskMapper,
            ProjectMapper projectMapper,
            NoteMapper noteMapper,
            WorkspaceAuthorizationService authorization,
            AuditService auditService
    ) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
        this.noteMapper = noteMapper;
        this.authorization = authorization;
        this.auditService = auditService;
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
    public PageResponse<TaskResponse> list(
            Long workspaceId, String keyword, Long projectId, TaskStatus status, TaskPriority priority,
            int page, int size
    ) {
        authorization.require(workspaceId, WorkspacePermission.TASK_READ);
        PageQuery query = PageQuery.of(page, size);
        var items = taskMapper.listPage(
                        workspaceId, keyword, projectId, status, priority, query.offset(), query.size()).stream()
                .map(TaskResponseAssembler::toResponse)
                .toList();
        return PageResponse.of(
                items, page, size, taskMapper.count(workspaceId, keyword, projectId, status, priority));
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     */
    public TaskResponse get(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.TASK_READ);
        return TaskResponseAssembler.toResponse(getEntity(workspaceId, id));
    }

    private Task getEntity(Long workspaceId, Long id) {
        Task task = taskMapper.findById(workspaceId, id);
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
    public TaskResponse create(Long workspaceId, TaskRequest request) {
        authorization.require(workspaceId, WorkspacePermission.TASK_WRITE);
        validate(workspaceId, request);
        Task task = new Task();
        task.setWorkspaceId(workspaceId);
        applyRequest(task, request);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        auditService.success(workspaceId, "TASK_CREATE", "TASK", task.getId());
        return get(workspaceId, task.getId());
    }

    /**
     * 更新任务完整信息。
     *
     * @param id 任务 ID
     * @param request 任务更新请求
     * @return 更新后的任务
     */
    @Transactional
    public TaskResponse update(Long workspaceId, Long id, TaskRequest request) {
        authorization.require(workspaceId, WorkspacePermission.TASK_WRITE);
        validate(workspaceId, request);
        getEntity(workspaceId, id);
        Task task = new Task();
        task.setId(id);
        task.setWorkspaceId(workspaceId);
        applyRequest(task, request);
        task.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        taskMapper.update(task);
        auditService.success(workspaceId, "TASK_UPDATE", "TASK", id);
        return get(workspaceId, id);
    }

    /**
     * 更新任务看板状态。
     *
     * @param id 任务 ID
     * @param status 新状态
     * @return 更新后的任务
     */
    @Transactional
    public TaskResponse updateStatus(Long workspaceId, Long id, TaskStatus status) {
        authorization.require(workspaceId, WorkspacePermission.TASK_WRITE);
        RequestChecks.requiredObject(status, "任务状态");
        getEntity(workspaceId, id);
        taskMapper.updateStatus(workspaceId, id, status, LocalDateTime.now(ZoneId.systemDefault()));
        auditService.success(workspaceId, "TASK_STATUS_UPDATE", "TASK", id);
        return get(workspaceId, id);
    }

    /**
     * 删除任务。
     *
     * @param id 任务 ID
     */
    @Transactional
    public void delete(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.TASK_WRITE);
        getEntity(workspaceId, id);
        taskMapper.delete(workspaceId, id);
        auditService.success(workspaceId, "TASK_DELETE", "TASK", id);
    }

    /**
     * 校验任务请求。
     *
     * <p>所属项目必须存在；状态和优先级由前端显式选择，不在后端填默认值。</p>
     */
    private void validate(Long workspaceId, TaskRequest request) {
        RequestChecks.requiredObject(request, "任务请求");
        RequestChecks.requiredText(request.title(), "任务标题");
        RequestChecks.requiredObject(request.projectId(), "所属项目");
        RequestChecks.requiredObject(request.status(), "任务状态");
        RequestChecks.requiredObject(request.priority(), "任务优先级");
        RequestChecks.maxLength(request.title().trim(), 200, "任务标题");
        RequestChecks.requiredExisting(projectMapper.findById(workspaceId, request.projectId()), "所属项目不存在");
        if (request.evidenceNoteId() != null) {
            var note = noteMapper.lockById(workspaceId, request.evidenceNoteId());
            RequestChecks.requiredExisting(note, "依据笔记不存在");
            if (!request.projectId().equals(note.getProjectId())) {
                throw new BusinessException("依据笔记必须属于任务项目");
            }
        }
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
        task.setEvidenceNoteId(request.evidenceNoteId());
    }
}
