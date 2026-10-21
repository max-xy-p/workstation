package com.mike.workstation.service;

import com.mike.workstation.domain.*;
import com.mike.workstation.mapper.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 定点结果提交与连接租约、关联代次、仓库绑定在同一短事务核对。 */
@Service
public class IssueTrackingStateService {
    private final IssueSourceMapper sources;
    private final IntegrationMapper integrations;
    private final IntegrationStateService state;
    private final GithubWebhookMapper webhooks;
    public IssueTrackingStateService(IssueSourceMapper sources,IntegrationMapper integrations,IntegrationStateService state,GithubWebhookMapper webhooks) {
        this.sources=sources;this.integrations=integrations;this.state=state;this.webhooks=webhooks;
    }
    @Transactional
    public void begin(IntegrationRun run) {
        state.heartbeat(run);
        if(run.getIssueUpperBound()==null) {
            run.setIssueUpperBound(sources.upperBound(run.getWorkspaceId()));
            integrations.updateRun(run);
        }
    }
    @Transactional
    public void complete(IntegrationRun run,IssueSource selected,DevelopmentItem fresh,String error) {
        state.heartbeat(run);
        apply(run,selected,fresh,error);
        run.setIssueCursor(selected.getId());run.setProcessed(run.getProcessed()+1);integrations.updateRun(run);
    }
    @Transactional
    public void completeTarget(IntegrationRun run,IssueSource selected,DevelopmentItem fresh,String error) {
        state.heartbeat(run);
        String outcome=apply(run,selected,fresh,error);
        webhooks.completeTarget(run.getWorkspaceId(),run.getId(),selected.getId(),outcome,
                "FAILED".equals(outcome)?"来源刷新失败，请查看任务来源详情":null);
        run.setProcessed(run.getProcessed()+1);integrations.updateRun(run);
    }
    private String apply(IntegrationRun run,IssueSource selected,DevelopmentItem fresh,String error) {
        integrations.lockRepository(run.getWorkspaceId(),selected.getRepositoryId());
        IssueSource stored=sources.lock(run.getWorkspaceId(),selected.getId());
        String currentToken=sources.linkToken(run.getWorkspaceId(),selected.getId());
        Long project=sources.boundProject(run.getWorkspaceId(),selected.getRepositoryId());
        ExternalRepository repo=integrations.repository(run.getWorkspaceId(),selected.getRepositoryId());
        if(stored==null || currentToken==null || !Objects.equals(currentToken,selected.getLinkToken())
                || !Objects.equals(project,selected.getProjectId()) || repo==null
                || !run.getConnectionId().equals(repo.getConnectionId())) return "CANCELLED";
        save(stored,fresh,error);
        if(stored.getLastError()!=null) { run.setFailed(run.getFailed()+1);return "FAILED"; }
        return "SUCCEEDED";
    }
    private void save(IssueSource stored,DevelopmentItem fresh,String error) {
        stored.setLastAttemptAt(LocalDateTime.now(ZoneId.systemDefault()));
        if(error==null && (fresh==null || !stored.getExternalId().equals(fresh.getExternalId())
                || !Objects.equals(stored.getIssueNumber(),fresh.getIssueNumber()))) error="来源身份已变化，请管理员核实";
        if(error==null && inconsistentAtSameTime(stored,fresh)) error="来源时间相同但内容不一致，保留原快照等待核实";
        if(error==null) {
            if(newerOrEqual(stored,fresh)) {
                stored.setTitle(fresh.getTitle());stored.setUrl(fresh.getUrl());stored.setStatus(fresh.getStatus());
                stored.setStateReason(fresh.getStateReason());stored.setSourceUpdatedAt(fresh.getSourceUpdatedAt());
            }
            stored.setLastSuccessAt(stored.getLastAttemptAt());
        }
        stored.setLastError(error);sources.update(stored);
    }
    private boolean newerOrEqual(IssueSource stored,DevelopmentItem fresh) {
        return stored.getSourceUpdatedAt()==null || (fresh.getSourceUpdatedAt()!=null
                && !fresh.getSourceUpdatedAt().isBefore(stored.getSourceUpdatedAt()));
    }
    private boolean inconsistentAtSameTime(IssueSource stored,DevelopmentItem fresh) {
        return stored.getSourceUpdatedAt()!=null && stored.getSourceUpdatedAt().equals(fresh.getSourceUpdatedAt())
                && (!Objects.equals(stored.getTitle(),fresh.getTitle()) || !Objects.equals(stored.getUrl(),fresh.getUrl())
                || !Objects.equals(stored.getStatus(),fresh.getStatus()) || !Objects.equals(stored.getStateReason(),fresh.getStateReason()));
    }
}
