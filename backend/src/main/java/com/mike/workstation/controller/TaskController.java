package com.mike.workstation.controller;

import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.dto.TaskRequest;
import com.mike.workstation.dto.TaskStatusRequest;
import com.mike.workstation.service.TaskService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
/**
 * 任务接口控制器。
 *
 * <p>提供任务看板所需的查询、创建、编辑、删除和状态流转接口。</p>
 */
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 查询任务列表。
     *
     * @param keyword 任务标题关键字，可为空
     * @param projectId 所属项目筛选，可为空
     * @param status 看板状态筛选，可为空
     * @param priority 优先级筛选，可为空
     * @return 满足筛选条件的任务列表
     */
    @GetMapping
    public List<Task> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority
    ) {
        return taskService.list(keyword, projectId, status, priority);
    }

    /**
     * 查询单个任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    public Task get(@PathVariable Long id) {
        return taskService.get(id);
    }

    /**
     * 创建任务。
     *
     * @param request 任务创建请求，标题、所属项目、状态、优先级必填
     * @return 创建后的任务数据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@RequestBody TaskRequest request) {
        return taskService.create(request);
    }

    /**
     * 更新任务完整信息。
     *
     * @param id 任务 ID
     * @param request 任务更新请求
     * @return 更新后的任务数据
     */
    @PutMapping("/{id}")
    public Task update(@PathVariable Long id, @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    /**
     * 单独更新任务状态。
     *
     * <p>主要用于看板页面中把任务移动到另一个状态列。</p>
     *
     * @param id 任务 ID
     * @param request 状态更新请求
     * @return 更新后的任务数据
     */
    @PatchMapping("/{id}/status")
    public Task updateStatus(@PathVariable Long id, @RequestBody TaskStatusRequest request) {
        return taskService.updateStatus(id, request.status());
    }

    /**
     * 删除任务。
     *
     * <p>删除任务不会影响所属项目。</p>
     *
     * @param id 任务 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        taskService.delete(id);
    }
}
