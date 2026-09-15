package com.mike.workstation.controller;

import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.dto.ProjectDetailResponse;
import com.mike.workstation.dto.ProjectRequest;
import com.mike.workstation.dto.ProjectResponse;
import com.mike.workstation.dto.ProjectSummaryResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.dto.ProjectStatusRequest;
import com.mike.workstation.service.ProjectService;
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
@RequestMapping("/api/projects")
/**
 * 项目接口控制器。
 *
 * <p>负责接收前端关于“软件项目”的 REST 请求，并把具体业务处理交给 {@link ProjectService}。
 * Controller 只做请求参数承接和响应返回，不直接编写数据库访问或删除规则。</p>
 */
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 查询项目列表。
     *
     * @param keyword 项目名称关键字，可为空
     * @param status 项目状态筛选，可为空
     * @return 按更新时间倒序排列的项目列表
     */
    @GetMapping
    public PageResponse<ProjectSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return projectService.list(keyword, status, page, size);
    }

    /**
     * 查询项目详情。
     *
     * <p>详情中除了项目本身，还包含该项目关联的任务和笔记。</p>
     *
     * @param id 项目 ID
     * @return 项目详情聚合数据
     */
    @GetMapping("/{id}")
    public ProjectDetailResponse detail(@PathVariable Long id) {
        return projectService.detail(id);
    }

    /**
     * 创建项目。
     *
     * @param request 项目创建请求，名称和状态必填
     * @return 创建后的项目数据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    /**
     * 更新项目完整信息。
     *
     * @param id 项目 ID
     * @param request 项目更新请求，名称和状态必填
     * @return 更新后的项目数据
     */
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    /**
     * 单独更新项目状态。
     *
     * <p>用于项目列表或详情页上的快捷状态切换。</p>
     *
     * @param id 项目 ID
     * @param request 状态更新请求
     * @return 更新后的项目数据
     */
    @PatchMapping("/{id}/status")
    public ProjectResponse updateStatus(@PathVariable Long id, @RequestBody ProjectStatusRequest request) {
        return projectService.updateStatus(id, request.status());
    }

    /**
     * 删除项目。
     *
     * <p>删除规则在 Service 层实现：存在任务时拒绝删除；仅有关联笔记时会清空笔记的项目关联。</p>
     *
     * @param id 项目 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }
}
