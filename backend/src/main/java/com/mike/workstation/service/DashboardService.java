package com.mike.workstation.service;

import com.mike.workstation.assembler.NoteResponseAssembler;
import com.mike.workstation.assembler.ProjectResponseAssembler;
import com.mike.workstation.config.DashboardProperties;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.DashboardResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TaskMapper;

import org.springframework.stereotype.Service;

/**
 * 首页概览业务服务。
 *
 * <p>负责聚合项目、任务和笔记数据，给首页一次性返回可展示的工作台概览。
 */
@Service
public class DashboardService {
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;
    private final NoteTagLoader noteTagLoader;
    private final DashboardProperties dashboardProperties;
    private final WorkspaceAuthorizationService authorization;

    public DashboardService(
            ProjectMapper projectMapper,
            TaskMapper taskMapper,
            NoteMapper noteMapper,
            NoteTagLoader noteTagLoader,
            DashboardProperties dashboardProperties,
            WorkspaceAuthorizationService authorization) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.noteMapper = noteMapper;
        this.noteTagLoader = noteTagLoader;
        this.dashboardProperties = dashboardProperties;
        this.authorization = authorization;
    }

    /**
     * 查询首页概览。
     *
     * @return 活跃项目、待办任务、进行中任务数量，以及最近项目和最近笔记
     */
    public DashboardResponse summary(Long workspaceId) {
        authorization.require(workspaceId, WorkspacePermission.DASHBOARD_READ);
        if (dashboardProperties.recentLimit() == null || dashboardProperties.recentLimit() < 1) {
            throw new BusinessException("首页最近记录数量未正确配置");
        }

        var recentNotes =
                noteMapper.recentSummaries(workspaceId, dashboardProperties.recentLimit());
        noteTagLoader.attachTags(workspaceId, recentNotes);
        return new DashboardResponse(
                projectMapper.countByStatus(workspaceId, ProjectStatus.ACTIVE),
                taskMapper.countByStatus(workspaceId, TaskStatus.TODO),
                taskMapper.countByStatus(workspaceId, TaskStatus.IN_PROGRESS),
                projectMapper.recent(workspaceId, dashboardProperties.recentLimit()).stream()
                        .map(ProjectResponseAssembler::toSummary)
                        .toList(),
                recentNotes.stream().map(NoteResponseAssembler::toSummary).toList());
    }
}
