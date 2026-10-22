package com.mike.workstation.service;

import com.mike.workstation.domain.DevelopmentItem;
import com.mike.workstation.domain.ExternalRepository;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IntegrationRun;
import com.mike.workstation.domain.IssueSource;
import com.mike.workstation.exception.IntegrationConflictException;
import com.mike.workstation.integration.RepositoryProvider;
import com.mike.workstation.mapper.GithubWebhookMapper;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.mapper.IssueSourceMapper;

import org.springframework.stereotype.Service;

import java.util.List;

/** 按持久化游标逐批跟踪全部已关联来源，网络请求不占用业务事务。 */
@Service
public class IssueTracker {
    private final IssueSourceMapper sources;
    private final IntegrationMapper integrations;
    private final IssueTrackingStateService state;
    private final IntegrationStateService leases;
    private final GithubWebhookMapper webhooks;

    public IssueTracker(
            IssueSourceMapper sources,
            IntegrationMapper integrations,
            IssueTrackingStateService state,
            IntegrationStateService leases,
            GithubWebhookMapper webhooks) {
        this.sources = sources;
        this.integrations = integrations;
        this.state = state;
        this.leases = leases;
        this.webhooks = webhooks;
    }

    public void track(
            IntegrationRun run,
            IntegrationConnection connection,
            RepositoryProvider provider,
            String token) {
        if (!"GITHUB".equals(connection.getProvider())) {
            return;
        }
        state.begin(run);
        List<IssueSource> batch;
        do {
            batch =
                    sources.pending(
                            run.getWorkspaceId(),
                            connection.getId(),
                            run.getIssueCursor(),
                            run.getIssueUpperBound());
            for (IssueSource source : batch) {
                refresh(run, provider, connection, token, source, false);
            }
        } while (!batch.isEmpty());
    }

    /** 指定刷新只处理持久化目标，不修改整批跟踪的上界和游标。 */
    public void trackTargets(
            IntegrationRun run,
            IntegrationConnection connection,
            RepositoryProvider provider,
            String token) {
        for (IssueSource source : webhooks.targets(run.getWorkspaceId(), run.getId())) {
            refresh(run, provider, connection, token, source, true);
        }
    }

    private void refresh(
            IntegrationRun run,
            RepositoryProvider provider,
            IntegrationConnection connection,
            String token,
            IssueSource source,
            boolean targeted) {
        leases.heartbeat(run);
        DevelopmentItem fresh = null;
        String error = null;
        // 网络等待放在事务外，避免远端延迟长时间占用来源与任务关联的数据库锁。
        try {
            ExternalRepository repository =
                    integrations.repository(run.getWorkspaceId(), source.getRepositoryId());
            if (repository == null) {
                error = "来源仓库不可读取";
            } else {
                fresh = provider.issue(connection, token, repository, source.getIssueNumber());
            }
        } catch (IntegrationConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            error = IntegrationWorker.safeError(ex);
        }
        if (targeted) {
            state.completeTarget(run, source, fresh, error);
        } else {
            state.complete(run, source, fresh, error);
        }
    }
}
