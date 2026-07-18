package com.mike.workstation.service;

import com.mike.workstation.config.DashboardProperties;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.dto.DashboardResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import com.mike.workstation.mapper.TaskMapper;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;
    private final DashboardProperties dashboardProperties;

    public DashboardService(
            ProjectMapper projectMapper,
            TaskMapper taskMapper,
            NoteMapper noteMapper,
            TagMapper tagMapper,
            DashboardProperties dashboardProperties
    ) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.noteMapper = noteMapper;
        this.tagMapper = tagMapper;
        this.dashboardProperties = dashboardProperties;
    }

    public DashboardResponse summary() {
        if (dashboardProperties.recentLimit() == null || dashboardProperties.recentLimit() < 1) {
            throw new BusinessException("首页最近记录数量未正确配置");
        }

        var recentNotes = noteMapper.recent(dashboardProperties.recentLimit());
        recentNotes.forEach(note -> note.setTags(tagMapper.findByNoteId(note.getId())));
        return new DashboardResponse(
                projectMapper.countByStatus(ProjectStatus.ACTIVE),
                taskMapper.countByStatus(TaskStatus.TODO),
                taskMapper.countByStatus(TaskStatus.IN_PROGRESS),
                projectMapper.recent(dashboardProperties.recentLimit()),
                recentNotes
        );
    }
}
