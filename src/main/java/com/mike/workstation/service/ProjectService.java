package com.mike.workstation.service;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.dto.ProjectDetailResponse;
import com.mike.workstation.dto.ProjectRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import com.mike.workstation.mapper.TaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 项目业务服务。
 *
 * <p>集中处理项目的校验、详情聚合和删除规则，Controller 不直接理解这些业务约束。</p>
 */
public class ProjectService {
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;

    public ProjectService(ProjectMapper projectMapper, TaskMapper taskMapper, NoteMapper noteMapper, TagMapper tagMapper) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.noteMapper = noteMapper;
        this.tagMapper = tagMapper;
    }

    /**
     * 按名称关键字和状态查询项目。
     *
     * @param keyword 名称关键字，可为空
     * @param status 项目状态，可为空
     * @return 项目列表
     */
    public List<Project> list(String keyword, ProjectStatus status) {
        return projectMapper.list(keyword, status);
    }

    /**
     * 查询项目基础信息。
     *
     * @param id 项目 ID
     * @return 项目实体
     * @throws com.mike.workstation.exception.NotFoundException 项目不存在时抛出
     */
    public Project get(Long id) {
        Project project = projectMapper.findById(id);
        RequestChecks.requiredExisting(project, "项目不存在");
        return project;
    }

    /**
     * 查询项目详情聚合数据。
     *
     * <p>项目详情页需要同时展示项目、关联任务和关联笔记，因此在这里组装成一个响应对象。</p>
     *
     * @param id 项目 ID
     * @return 项目详情、关联任务和关联笔记
     */
    public ProjectDetailResponse detail(Long id) {
        Project project = get(id);
        List<Note> notes = noteMapper.list(null, null, null, id);
        notes.forEach(note -> note.setTags(tagMapper.findByNoteId(note.getId())));
        return new ProjectDetailResponse(project, taskMapper.list(null, id, null, null), notes);
    }

    /**
     * 创建项目。
     *
     * @param request 项目创建请求
     * @return 创建后的项目
     */
    @Transactional
    public Project create(ProjectRequest request) {
        validate(request);
        Project project = new Project();
        applyRequest(project, request);
        LocalDateTime now = LocalDateTime.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);
        return get(project.getId());
    }

    /**
     * 更新项目完整信息。
     *
     * @param id 项目 ID
     * @param request 项目更新请求
     * @return 更新后的项目
     */
    @Transactional
    public Project update(Long id, ProjectRequest request) {
        validate(request);
        get(id);
        Project project = new Project();
        project.setId(id);
        applyRequest(project, request);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.update(project);
        return get(id);
    }

    /**
     * 更新项目状态。
     *
     * @param id 项目 ID
     * @param status 新状态
     * @return 更新后的项目
     */
    @Transactional
    public Project updateStatus(Long id, ProjectStatus status) {
        RequestChecks.requiredObject(status, "项目状态");
        get(id);
        projectMapper.updateStatus(id, status, LocalDateTime.now());
        return get(id);
    }

    /**
     * 删除项目。
     *
     * <p>项目下存在任务时拒绝删除；仅有关联笔记时，先解除笔记关联再删除项目。</p>
     *
     * @param id 项目 ID
     */
    @Transactional
    public void delete(Long id) {
        get(id);
        if (taskMapper.countByProjectId(id) > 0) {
            throw new BusinessException("项目下存在任务，不能直接删除");
        }

        // 项目仅被笔记引用时允许删除，同时解除这些笔记的项目关联。
        noteMapper.clearProject(id, LocalDateTime.now());
        projectMapper.delete(id);
    }

    /**
     * 校验项目请求。
     *
     * <p>项目状态不使用后端默认值，调用方必须明确传入。</p>
     */
    private void validate(ProjectRequest request) {
        RequestChecks.requiredObject(request, "项目请求");
        RequestChecks.requiredText(request.name(), "项目名称");
        RequestChecks.requiredObject(request.status(), "项目状态");
    }

    /**
     * 把请求对象复制到领域对象。
     *
     * <p>单独拆出是为了让创建和更新共用同一套字段映射。</p>
     */
    private void applyRequest(Project project, ProjectRequest request) {
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(request.status());
        project.setRepositoryUrl(request.repositoryUrl());
        project.setTechStack(request.techStack());
        project.setRunGuide(request.runGuide());
    }
}
