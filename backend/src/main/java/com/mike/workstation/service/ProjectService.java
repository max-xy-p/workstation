package com.mike.workstation.service;

import com.mike.workstation.assembler.NoteResponseAssembler;
import com.mike.workstation.assembler.ProjectResponseAssembler;
import com.mike.workstation.assembler.TaskResponseAssembler;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.dto.ProjectDetailResponse;
import com.mike.workstation.dto.ProjectRequest;
import com.mike.workstation.dto.ProjectResponse;
import com.mike.workstation.dto.ProjectSummaryResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 项目业务服务。
 *
 * <p>集中处理项目的校验、详情聚合和删除规则，Controller 不直接理解这些业务约束。
 */
@Service
public class ProjectService {
    private static final String PROJECT_RESOURCE = "PROJECT";
    private final ProjectMapper projectMapper;
    private final IntegrationMapper integrationMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;
    private final NoteTagLoader noteTagLoader;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;

    public ProjectService(
            IntegrationMapper integrationMapper,
            ProjectMapper projectMapper,
            TaskMapper taskMapper,
            NoteMapper noteMapper,
            NoteTagLoader noteTagLoader,
            WorkspaceAuthorizationService authorization,
            AuditService auditService) {
        this.projectMapper = projectMapper;
        this.integrationMapper = integrationMapper;
        this.taskMapper = taskMapper;
        this.noteMapper = noteMapper;
        this.noteTagLoader = noteTagLoader;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    /**
     * 按名称关键字和状态查询项目。
     *
     * @param keyword 名称关键字，可为空
     * @param status 项目状态，可为空
     * @return 项目列表
     */
    public PageResponse<ProjectSummaryResponse> list(
            Long workspaceId, String keyword, ProjectStatus status, int page, int size) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_READ);
        PageQuery query = PageQuery.of(page, size);
        var items =
                projectMapper
                        .listPage(workspaceId, keyword, status, query.offset(), query.size())
                        .stream()
                        .map(ProjectResponseAssembler::toSummary)
                        .toList();
        return PageResponse.of(
                items, page, size, projectMapper.count(workspaceId, keyword, status));
    }

    /**
     * 查询项目基础信息。
     *
     * @param id 项目 ID
     * @return 项目实体
     * @throws com.mike.workstation.exception.NotFoundException 项目不存在时抛出
     */
    public ProjectResponse get(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_READ);
        return ProjectResponseAssembler.toResponse(getEntity(workspaceId, id));
    }

    private Project getEntity(Long workspaceId, Long id) {
        Project project = projectMapper.findById(workspaceId, id);
        RequestChecks.requiredExisting(project, "项目不存在");
        return project;
    }

    /**
     * 查询项目详情聚合数据。
     *
     * <p>项目详情页需要同时展示项目、关联任务和关联笔记，因此在这里组装成一个响应对象。
     *
     * @param id 项目 ID
     * @return 项目详情、关联任务和关联笔记
     */
    public ProjectDetailResponse detail(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_READ);
        Project project = getEntity(workspaceId, id);
        List<Note> notes = noteMapper.listSummariesByProjectId(workspaceId, id);
        noteTagLoader.attachTags(workspaceId, notes);
        return new ProjectDetailResponse(
                ProjectResponseAssembler.toResponse(project),
                taskMapper.listByProjectId(workspaceId, id).stream()
                        .map(TaskResponseAssembler::toResponse)
                        .toList(),
                notes.stream().map(NoteResponseAssembler::toSummary).toList());
    }

    /**
     * 创建项目。
     *
     * @param request 项目创建请求
     * @return 创建后的项目
     */
    @Transactional
    public ProjectResponse create(Long workspaceId, ProjectRequest request) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_WRITE);
        validate(request);
        Project project = new Project();
        project.setWorkspaceId(workspaceId);
        applyRequest(project, request);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);
        auditService.success(workspaceId, "PROJECT_CREATE", PROJECT_RESOURCE, project.getId());
        return get(workspaceId, project.getId());
    }

    /**
     * 更新项目完整信息。
     *
     * @param id 项目 ID
     * @param request 项目更新请求
     * @return 更新后的项目
     */
    @Transactional
    public ProjectResponse update(Long workspaceId, Long id, ProjectRequest request) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_WRITE);
        validate(request);
        getEntity(workspaceId, id);
        Project project = new Project();
        project.setId(id);
        project.setWorkspaceId(workspaceId);
        applyRequest(project, request);
        project.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        projectMapper.update(project);
        auditService.success(workspaceId, "PROJECT_UPDATE", PROJECT_RESOURCE, id);
        return get(workspaceId, id);
    }

    /**
     * 更新项目状态。
     *
     * @param id 项目 ID
     * @param status 新状态
     * @return 更新后的项目
     */
    @Transactional
    public ProjectResponse updateStatus(Long workspaceId, Long id, ProjectStatus status) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_WRITE);
        RequestChecks.requiredObject(status, "项目状态");
        getEntity(workspaceId, id);
        projectMapper.updateStatus(
                workspaceId, id, status, LocalDateTime.now(ZoneId.systemDefault()));
        auditService.success(workspaceId, "PROJECT_STATUS_UPDATE", PROJECT_RESOURCE, id);
        return get(workspaceId, id);
    }

    /**
     * 删除项目。
     *
     * <p>项目下存在任务时拒绝删除；仅有关联笔记时，先解除笔记关联再删除项目。
     *
     * @param id 项目 ID
     */
    @Transactional
    public void delete(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.PROJECT_WRITE);
        getEntity(workspaceId, id);
        if (taskMapper.countByProjectId(workspaceId, id) > 0) {
            throw new BusinessException("项目下存在任务，不能直接删除");
        }

        // 项目仅被笔记引用时允许删除，同时解除这些笔记的项目关联。
        noteMapper.clearProject(workspaceId, id, LocalDateTime.now(ZoneId.systemDefault()));
        integrationMapper.suppressProject(workspaceId, id);
        projectMapper.delete(workspaceId, id);
        auditService.success(workspaceId, "PROJECT_DELETE", PROJECT_RESOURCE, id);
    }

    /**
     * 校验项目请求。
     *
     * <p>项目状态不使用后端默认值，调用方必须明确传入。
     */
    private void validate(ProjectRequest request) {
        RequestChecks.requiredObject(request, "项目请求");
        RequestChecks.requiredText(request.name(), "项目名称");
        RequestChecks.requiredObject(request.status(), "项目状态");
        RequestChecks.maxLength(request.name().trim(), 200, "项目名称");
        RequestChecks.maxLength(request.repositoryUrl(), 500, "仓库地址");
        RequestChecks.maxLength(request.homepageUrl(), 500, "项目入口");
        if (request.homepageUrl() != null && !request.homepageUrl().isBlank()) {
            URI url = URI.create(request.homepageUrl());
            if ((!"https".equals(url.getScheme()) && !"http".equals(url.getScheme()))
                    || url.getHost() == null) {
                throw new BusinessException("项目入口必须为 HTTP 或 HTTPS 地址");
            }
        }
        RequestChecks.maxLength(request.techStack(), 500, "技术栈");
    }

    /**
     * 把请求对象复制到领域对象。
     *
     * <p>单独拆出是为了让创建和更新共用同一套字段映射。
     */
    private void applyRequest(Project project, ProjectRequest request) {
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setStatus(request.status());
        project.setRepositoryUrl(request.repositoryUrl());
        project.setHomepageUrl(request.homepageUrl());
        project.setTechStack(request.techStack());
        project.setRunGuide(request.runGuide());
    }
}
