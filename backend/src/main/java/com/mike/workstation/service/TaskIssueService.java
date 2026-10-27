package com.mike.workstation.service;

import com.mike.workstation.assembler.IssueSourceAssembler;
import com.mike.workstation.domain.DevelopmentItem;
import com.mike.workstation.domain.ExternalRepository;
import com.mike.workstation.domain.IssueSource;
import com.mike.workstation.domain.TaskStatus;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceType;
import com.mike.workstation.dto.IssueSourceConfirmRequest;
import com.mike.workstation.dto.IssueTaskCreateRequest;
import com.mike.workstation.dto.IssueTaskLinkRequest;
import com.mike.workstation.dto.IssueTaskResult;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.dto.TaskIssueSourceResponse;
import com.mike.workstation.dto.TaskResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.TaskIssueConflictException;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.mapper.IssueSourceMapper;
import com.mike.workstation.security.CurrentPrincipalResolver;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 来源关联短事务；Workspace 行锁串行化关联、任务移动及删除。 */
@Service
public class TaskIssueService {
    private final IssueSourceMapper sources;
    private final IntegrationMapper integrations;
    private final TaskService tasks;
    private final WorkspaceAuthorizationService authorization;
    private final CurrentPrincipalResolver principals;
    private final AuditService audit;

    public TaskIssueService(
            IssueSourceMapper sources,
            IntegrationMapper integrations,
            TaskService tasks,
            WorkspaceAuthorizationService authorization,
            CurrentPrincipalResolver principals,
            AuditService audit) {
        this.sources = sources;
        this.integrations = integrations;
        this.tasks = tasks;
        this.authorization = authorization;
        this.principals = principals;
        this.audit = audit;
    }

    public PageResponse<TaskIssueSourceResponse> list(Long w, Long project, int page, int size) {
        authorization.require(w, WorkspacePermission.TASK_READ);
        authorization.require(w, WorkspacePermission.PROJECT_READ);
        PageQuery q = PageQuery.of(page, size);
        return PageResponse.of(
                sources.list(w, project, q.offset(), q.size()).stream()
                        .map(IssueSourceAssembler::response)
                        .toList(),
                page,
                size,
                sources.count(w, project));
    }

    private void writable(Long w, Boolean confirmed) {
        Workspace space = authorization.require(w, WorkspacePermission.TASK_WRITE);
        authorization.require(w, WorkspacePermission.PROJECT_READ);
        if (space.getType() == WorkspaceType.SHARED && !Boolean.TRUE.equals(confirmed)) {
            throw new BusinessException("请确认来源快照将对工作空间成员可见");
        }
        sources.lockWorkspace(w);
    }

    /** 将可重建的近期采集记录解析为稳定来源；已有来源不被较旧的候选快照覆盖。 */
    private IssueSource source(Long w, Long project, Long candidateId) {
        RequestChecks.requiredObject(candidateId, "已采集 Issue");
        DevelopmentItem item = sources.candidate(w, candidateId);
        RequestChecks.requiredExisting(item, "已采集记录已变化，请刷新后重新选择");
        if (!"ISSUE".equals(item.getKind())) {
            throw new BusinessException("仅支持 GitHub Issue");
        }
        integrations.lockRepository(w, item.getRepositoryId());
        ExternalRepository repo = integrations.repository(w, item.getRepositoryId());
        RequestChecks.requiredExisting(repo, "仓库不存在");
        if (!"GITHUB".equals(repo.getProvider())
                || !project.equals(sources.boundProject(w, repo.getId()))) {
            throw new BusinessException("Issue 仓库必须属于当前项目且来自 GitHub");
        }
        if (item.getIssueNumber() == null || item.getIssueNumber() <= 0) {
            throw new BusinessException("来源缺少 Issue 编号，请管理员同步仓库后重试");
        }
        IssueSource source = new IssueSource();
        source.setWorkspaceId(w);
        source.setRepositoryId(repo.getId());
        source.setProvider(repo.getProvider());
        source.setBaseUrl(repo.getBaseUrl());
        source.setExternalId(item.getExternalId());
        source.setIssueNumber(item.getIssueNumber());
        source.setTitle(item.getTitle());
        source.setUrl(item.getUrl());
        source.setStatus(item.getStatus());
        source.setStateReason(item.getStateReason());
        source.setSourceUpdatedAt(item.getSourceUpdatedAt());
        // 记录级精确成功时间只在后续定点请求成功时写入，不能冒用仓库整体同步时间。
        sources.ensure(source);
        IssueSource stored = sources.identity(source);
        if (!repo.getId().equals(stored.getRepositoryId())) {
            throw new TaskIssueConflictException("来源仓库已变化，请先核实原关联");
        }
        return stored;
    }

    /** 创建任务与建立关联共用事务；相同来源的重复请求返回原任务。 */
    @Transactional
    public IssueTaskResult create(Long w, Long project, IssueTaskCreateRequest request) {
        writable(w, request.sharingConfirmed());
        IssueSource s = source(w, project, request.developmentItemId());
        Long existing = sources.linkedTask(w, s.getId());
        if (existing != null) {
            return new IssueTaskResult(tasks.getLocked(w, existing), false);
        }
        RequestChecks.requiredObject(request.task(), "任务内容");
        if (!project.equals(request.task().projectId())) {
            throw new BusinessException("任务必须属于来源项目");
        }
        TaskResponse task = tasks.create(w, request.task());
        attach(w, task.id(), s.getId());
        return new IssueTaskResult(task, true);
    }

    @Transactional
    public IssueTaskResult link(Long w, Long project, IssueTaskLinkRequest request) {
        writable(w, request.sharingConfirmed());
        RequestChecks.requiredObject(request.taskId(), "任务");
        IssueSource s = source(w, project, request.developmentItemId());
        TaskResponse task = tasks.getLocked(w, request.taskId());
        if (!project.equals(task.projectId())) {
            throw new BusinessException("任务必须属于来源项目");
        }
        Long existing = sources.linkedTask(w, s.getId());
        if (existing != null && !existing.equals(task.id())) {
            throw new TaskIssueConflictException("Issue 已关联其他任务");
        }
        Long current = sources.sourceForTask(w, task.id());
        if (current != null && !current.equals(s.getId())) {
            throw new TaskIssueConflictException("任务已有其他来源");
        }
        if (existing == null) {
            attach(w, task.id(), s.getId());
        }
        return new IssueTaskResult(task, false);
    }

    private void attach(Long w, Long task, Long source) {
        sources.link(w, task, source, principals.require().subjectId());
        audit.success(w, "TASK_ISSUE_LINK", "TASK", task);
    }

    /** 确认用户实际查看的来源差异，并在同一事务中提交可选的任务状态修改与审计。 任何前置版本变化都要求重新查看，不能把迟到请求解释为对新内容的确认。 */
    @Transactional
    public TaskIssueSourceResponse confirm(Long w, Long task, IssueSourceConfirmRequest request) {
        authorization.require(w, WorkspacePermission.TASK_WRITE);
        authorization.require(w, WorkspacePermission.PROJECT_READ);
        RequestChecks.requiredObject(request, "确认请求");
        RequestChecks.requiredText(request.expectedLinkToken(), "关联代次");
        RequestChecks.requiredObject(request.expectedSourceVersion(), "来源版本");
        RequestChecks.requiredObject(request.expectedConfirmationVersion(), "确认版本");
        RequestChecks.requiredObject(request.expectedTaskVersion(), "任务版本");
        RequestChecks.requiredObject(request.action(), "处理方式");
        sources.lockWorkspace(w);
        IssueSource selected = sources.forTask(w, task);
        if (selected == null) {
            throw new TaskIssueConflictException("来源关联已变化，请刷新后重试");
        }
        // 与后台提交保持连接、仓库、来源的锁定顺序，确认不发起平台调用。
        ExternalRepository repository = integrations.repository(w, selected.getRepositoryId());
        RequestChecks.requiredExisting(repository, "来源仓库不存在");
        if (repository.getConnectionId() != null) {
            integrations.lockConnection(w, repository.getConnectionId());
        }
        integrations.lockRepository(w, selected.getRepositoryId());
        TaskResponse local = tasks.getLocked(w, task);
        IssueSource current = sources.forTaskLocked(w, task);
        checkConfirmationVersions(current, request);
        if (!current.hasPendingChanges()) {
            throw new TaskIssueConflictException("当前已没有来源差异，请刷新查看");
        }
        applyConfirmationAction(w, task, local, current, request.action());
        sources.confirm(
                w,
                task,
                current.getLinkToken(),
                current.getTitle(),
                current.getStatus(),
                current.getStateReason(),
                principals.require().subjectId());
        audit.success(w, "TASK_ISSUE_CONFIRM", "TASK", task);
        return IssueSourceAssembler.response(sources.forTaskLocked(w, task));
    }

    // 四类版本分别保护关联身份、外部内容、其他成员的确认结果和本地任务修改。
    private void checkConfirmationVersions(IssueSource source, IssueSourceConfirmRequest request) {
        if (source == null
                || !Objects.equals(source.getLinkToken(), request.expectedLinkToken())
                || !Objects.equals(source.getSourceVersion(), request.expectedSourceVersion())
                || !Objects.equals(
                        source.getConfirmationVersion(), request.expectedConfirmationVersion())
                || !Objects.equals(source.getTaskVersion(), request.expectedTaskVersion())) {
            throw new TaskIssueConflictException("来源、确认结果或任务已变化，请刷新后重新查看");
        }
    }

    private void applyConfirmationAction(
            Long w,
            Long task,
            TaskResponse local,
            IssueSource source,
            IssueSourceConfirmRequest.Action action) {
        if (action == IssueSourceConfirmRequest.Action.KEEP) {
            return;
        }
        if (!"ACTIVE".equals(source.getTrackingStatus())) {
            throw new TaskIssueConflictException("来源尚未成功刷新或跟踪不可用，可保留任务并确认已知差异");
        }
        if (action == IssueSourceConfirmRequest.Action.COMPLETE) {
            if (!"CLOSED".equals(source.getStatus()) || local.status() == TaskStatus.DONE) {
                throw new TaskIssueConflictException("当前来源或任务状态不适合完成操作");
            }
            tasks.updateStatus(w, task, TaskStatus.DONE, local.version());
        } else {
            if (!"OPEN".equals(source.getStatus()) || local.status() != TaskStatus.DONE) {
                throw new TaskIssueConflictException("当前来源或任务状态不适合重新开启");
            }
            tasks.updateStatus(w, task, TaskStatus.IN_PROGRESS, local.version());
        }
    }

    @Transactional
    public void unlink(Long w, Long task) {
        authorization.require(w, WorkspacePermission.TASK_WRITE);
        sources.lockWorkspace(w);
        tasks.get(w, task);
        if (sources.unlink(w, task) > 0) {
            audit.success(w, "TASK_ISSUE_UNLINK", "TASK", task);
        }
    }
}
