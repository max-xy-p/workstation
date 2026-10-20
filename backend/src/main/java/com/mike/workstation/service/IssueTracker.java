package com.mike.workstation.service;

import com.mike.workstation.domain.*;
import com.mike.workstation.integration.RepositoryProvider;
import com.mike.workstation.mapper.*;
import com.mike.workstation.exception.IntegrationConflictException;
import java.util.List;
import org.springframework.stereotype.Service;

/** 按持久化游标逐批跟踪全部已关联来源，网络请求不占用业务事务。 */
@Service
public class IssueTracker {
    private final IssueSourceMapper sources;
    private final IntegrationMapper integrations;
    private final IssueTrackingStateService state;
    private final IntegrationStateService leases;
    public IssueTracker(IssueSourceMapper sources,IntegrationMapper integrations,IssueTrackingStateService state,
            IntegrationStateService leases) {
        this.sources=sources;this.integrations=integrations;this.state=state;this.leases=leases;
    }
    public void track(IntegrationRun run,IntegrationConnection connection,RepositoryProvider provider,String token) {
        if(!"GITHUB".equals(connection.getProvider())) return;
        state.begin(run);
        List<IssueSource> batch;
        do {
            batch=sources.pending(run.getWorkspaceId(),connection.getId(),run.getIssueCursor(),run.getIssueUpperBound());
            for(IssueSource source:batch) refresh(run,provider,connection,token,source);
        } while(!batch.isEmpty());
    }
    private void refresh(IntegrationRun run,RepositoryProvider provider,IntegrationConnection connection,String token,IssueSource source) {
        leases.heartbeat(run);
        DevelopmentItem fresh=null;String error=null;
        try {
            ExternalRepository repository=integrations.repository(run.getWorkspaceId(),source.getRepositoryId());
            if(repository==null) error="来源仓库不可读取";
            else fresh=provider.issue(connection,token,repository,source.getIssueNumber());
        } catch(IntegrationConflictException ex) { throw ex; }
        catch(RuntimeException ex) { error=IntegrationWorker.safeError(ex); }
        state.complete(run,source,fresh,error);
    }
}
