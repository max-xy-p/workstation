package com.mike.workstation.service;

import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.IntegrationConflictException;
import com.mike.workstation.integration.CredentialCipher;
import com.mike.workstation.integration.RepositoryProvider;
import com.mike.workstation.mapper.IntegrationMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.xxl.job.core.handler.annotation.XxlJob;

/** 持久化队列的有界执行器；重启后从租约和逐项记录恢复。 */
@Component
@ConditionalOnProperty(prefix="workstation.integrations",name="enabled",havingValue="true")
public class IntegrationWorker {
    private final IntegrationMapper mapper;
    private final IntegrationStateService state;
    private final IntegrationService service;
    private final CredentialCipher cipher;
    private final IntegrationProperties properties;
    private final IssueTracker issueTracker;
    private final GithubWebhookDispatcher webhooks;
    public IntegrationWorker(IntegrationMapper mapper,IntegrationStateService state,IntegrationService service,
            CredentialCipher cipher,IntegrationProperties properties,IssueTracker issueTracker,GithubWebhookDispatcher webhooks) {
        this.mapper=mapper; this.state=state; this.service=service; this.cipher=cipher; this.properties=properties; this.issueTracker=issueTracker;this.webhooks=webhooks;
    }
    @Scheduled(fixedDelay=3000)
    @XxlJob("integrationQueueJob")
    public void execute() {
        for(GithubWebhook hook:webhooks.pending()) {
            try { webhooks.dispatch(hook.getWorkspaceId(),hook.getConnectionId()); }
            catch(RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(IntegrationWorker.class).warn("Webhook 通知调度失败，将在后续周期重试");
            }
        }
        IntegrationRun run=state.claim(); if(run==null) return;
        try {
            state.heartbeat(run);
            IntegrationConnection c=service.require(run.getWorkspaceId(),run.getConnectionId());
            RepositoryProvider provider=service.provider(c.getProvider());
            String token=cipher.decrypt(c.getCredential(),c.getKeyVersion(),IntegrationService.context(c));
            if("ISSUE_REFRESH".equals(run.getKind())) issueTracker.trackTargets(run,c,provider,token);
            else if("IMPORT".equals(run.getKind())) imports(run);
            else {
                if("DISCOVER".equals(run.getKind()) || c.isAutoImport()) discover(run,c,provider,token);
                if("SYNC".equals(run.getKind())) {
                    sync(run,c,provider,token);
                    issueTracker.track(run,c,provider,token);
                }
            }
            state.finish(run,run.getFailed()==0?"SUCCEEDED":"PARTIAL",run.getFailed()==0?null:"部分资源未能同步，请查看仓库及逐项结果");
        } catch(IntegrationConflictException ex) {
            state.finish(run,"CANCELLED","连接配置、权限或运行租约已变化");
        } catch(RuntimeException ex) {
            state.finish(run,"FAILED",safeError(ex));
        }
    }
    @Scheduled(fixedDelay=30000)
    @XxlJob("integrationScheduleJob")
    public void schedule() {
        for(IntegrationConnection c:mapper.dueConnections()) state.schedule(c.getWorkspaceId(),c.getId());
    }
    private void discover(IntegrationRun run,IntegrationConnection c,RepositoryProvider p,String token) {
        for(int page=1;page<=properties.maxPages();page++) {
            state.heartbeat(run);
            List<ExternalRepository> rows=p.repositories(c,token,page);
            state.saveRepositories(run,rows);
            if(rows.size()<100) return;
        }
        throw new BusinessException("仓库发现超过分页上限，本次结果不完整，请缩小范围或调整部署上限");
    }
    private void imports(IntegrationRun run) {
        long offset=0;
        List<IntegrationRunItem> items;
        do {
            items=mapper.items(run.getWorkspaceId(),run.getId(),offset,100);
            for(var item:items) {
                if("SUCCEEDED".equals(item.getStatus()) || "FAILED".equals(item.getStatus())) continue;
                try { state.importItem(run,item); }
                catch(RuntimeException ex) { state.failedItem(run,item,safeError(ex)); }
            }
            offset+=items.size();
        } while(items.size()==100);
    }
    private void sync(IntegrationRun run,IntegrationConnection c,RepositoryProvider p,String token) {
        long offset=0;
        List<ExternalRepository> repositories;
        do {
            repositories=mapper.repositories(c.getWorkspaceId(),c.getId(),-1L,offset,100);
            for(var stored:repositories) {
                state.heartbeat(run);
                try {
                    ExternalRepository fresh=p.repository(c,token,stored);
                    state.refreshRepository(run,fresh);
                    fresh.setId(stored.getId());
                    for(DevelopmentKind kind:DevelopmentKind.values()) development(run,c,p,token,fresh,kind);
                } catch(IntegrationConflictException ex) { throw ex; }
                catch(RuntimeException ex) { state.repositoryError(run,stored.getId(),safeError(ex)); }
            }
            offset+=repositories.size();
        } while(repositories.size()==100);
    }
    private void development(IntegrationRun run,IntegrationConnection c,RepositoryProvider provider,String token,
            ExternalRepository repository,DevelopmentKind kind) {
        LocalDateTime since=LocalDateTime.now(ZoneId.systemDefault()).minusDays(properties.recentDays());
        DevelopmentSnapshot snapshot=new DevelopmentSnapshot();
        snapshot.setKind(kind.name()); snapshot.setSince(since);
        List<DevelopmentItem> collected=new ArrayList<>();
        try {
            boolean finished=false;
            for(int page=1;page<=properties.maxPages() && !finished;page++) {
                state.heartbeat(run);
                List<DevelopmentItem> rows=provider.development(c,token,repository,kind,since,page);
                boolean pastWindow=collectRecent(rows,kind,since,collected);
                if(collected.size()>properties.maxItems()) {
                    collected=new ArrayList<>(collected.subList(0,properties.maxItems()));
                    snapshot.setTruncated(true); finished=true;
                } else finished=rows.size()<100 || pastWindow;
                if(page==properties.maxPages() && !finished) snapshot.setTruncated(true);
            }
            snapshot.setAvailable(true); snapshot.setCollected(collected.size());
            snapshot.setOpenCount((int)collected.stream().filter(i->"OPEN".equals(i.getStatus())).count());
        } catch(IntegrationConflictException ex) { throw ex; }
        catch(RuntimeException ex) {
            snapshot.setAvailable(false); snapshot.setMessage(safeError(ex)); collected=List.of();
            state.repositoryError(run,repository.getId(),snapshot.getMessage());
        }
        state.snapshot(run,repository,snapshot,collected);
    }
    private boolean collectRecent(List<DevelopmentItem> rows,DevelopmentKind kind,LocalDateTime since,
            List<DevelopmentItem> collected) {
        collected.addAll(rows.stream().filter(item -> !beforeWindow(item,since))
                .filter(item -> kind.name().equals(item.getKind())).toList());
        return rows.stream().anyMatch(item -> beforeWindow(item,since));
    }
    private boolean beforeWindow(DevelopmentItem item,LocalDateTime since) {
        return item.getSourceUpdatedAt()!=null && item.getSourceUpdatedAt().isBefore(since);
    }
    static String safeError(RuntimeException ex) {
        return ex instanceof BusinessException?ex.getMessage():"同步失败，请检查连接配置或联系管理员";
    }
}
