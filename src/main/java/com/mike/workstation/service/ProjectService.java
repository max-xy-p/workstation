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

    public List<Project> list(String keyword, ProjectStatus status) {
        return projectMapper.list(keyword, status);
    }

    public Project get(Long id) {
        Project project = projectMapper.findById(id);
        RequestChecks.requiredExisting(project, "项目不存在");
        return project;
    }

    public ProjectDetailResponse detail(Long id) {
        Project project = get(id);
        List<Note> notes = noteMapper.list(null, null, null, id);
        notes.forEach(note -> note.setTags(tagMapper.findByNoteId(note.getId())));
        return new ProjectDetailResponse(project, taskMapper.list(null, id, null, null), notes);
    }

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

    @Transactional
    public Project updateStatus(Long id, ProjectStatus status) {
        RequestChecks.requiredObject(status, "项目状态");
        get(id);
        projectMapper.updateStatus(id, status, LocalDateTime.now());
        return get(id);
    }

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

    private void validate(ProjectRequest request) {
        RequestChecks.requiredObject(request, "项目请求");
        RequestChecks.requiredText(request.name(), "项目名称");
        RequestChecks.requiredObject(request.status(), "项目状态");
    }

    private void applyRequest(Project project, ProjectRequest request) {
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(request.status());
        project.setRepositoryUrl(request.repositoryUrl());
        project.setTechStack(request.techStack());
        project.setRunGuide(request.runGuide());
    }
}
