package com.mike.workstation.service;

import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.DevelopmentItem;
import com.mike.workstation.domain.DevelopmentKind;
import com.mike.workstation.domain.DevelopmentSnapshot;
import com.mike.workstation.domain.ExternalRepository;
import com.mike.workstation.domain.GithubWebhook;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IntegrationRun;
import com.mike.workstation.domain.IntegrationRunItem;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.ExternalApiDeferredException;
import com.mike.workstation.exception.IntegrationConflictException;
import com.mike.workstation.integration.CredentialCipher;
import com.mike.workstation.integration.IntegrationProgress;
import com.mike.workstation.integration.RepositoryProvider;
import com.mike.workstation.mapper.IntegrationMapper;
import com.xxl.job.core.handler.annotation.XxlJob;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 持久化队列的有界执行器；重启后从租约和逐项记录恢复。 */
@Component
@ConditionalOnProperty(prefix = "workstation.integrations", name = "enabled", havingValue = "true")
public class IntegrationWorker {
    private final IntegrationMapper mapper;
    private final IntegrationStateService state;
    private final IntegrationService service;
    private final CredentialCipher cipher;
    private final IntegrationProperties properties;
    private final IssueTracker issueTracker;
    private final GithubWebhookDispatcher webhooks;

    public IntegrationWorker(
            IntegrationMapper mapper,
            IntegrationStateService state,
            IntegrationService service,
            CredentialCipher cipher,
            IntegrationProperties properties,
            IssueTracker issueTracker,
            GithubWebhookDispatcher webhooks) {
        this.mapper = mapper;
        this.state = state;
        this.service = service;
        this.cipher = cipher;
        this.properties = properties;
        this.issueTracker = issueTracker;
        this.webhooks = webhooks;
    }

    @Scheduled(fixedDelay = 3000)
    @XxlJob("integrationQueueJob")
    public void execute() {
        dispatchWebhooks();
        IntegrationRun run = state.claim();
        if (run == null) {
            return;
        }
        try {
            IntegrationProgress progress = IntegrationProgress.read(run);
            state.heartbeat(run);
            IntegrationConnection c = service.require(run.getWorkspaceId(), run.getConnectionId());
            RepositoryProvider provider = service.provider(c.getProvider());
            String token =
                    cipher.decrypt(
                            c.getCredential(), c.getKeyVersion(), IntegrationService.context(c));
            process(run, c, provider, token, progress);
            state.finish(
                    run,
                    run.getFailed() == 0 ? "SUCCEEDED" : "PARTIAL",
                    run.getFailed() == 0 ? null : "部分资源未能同步，请查看仓库及逐项结果");
        } catch (ExternalApiDeferredException ex) {
            try {
                state.defer(run, ex.delay(), ex.getMessage());
            } catch (IntegrationConflictException cancelled) {
                state.finish(run, "CANCELLED", "连接配置、权限或运行租约已变化");
            }
        } catch (IntegrationConflictException ex) {
            state.finish(run, "CANCELLED", "连接配置、权限或运行租约已变化");
        } catch (RuntimeException ex) {
            state.finish(run, "FAILED", safeError(ex));
        }
    }

    private void dispatchWebhooks() {
        for (GithubWebhook hook : webhooks.pending()) {
            try {
                webhooks.dispatch(hook.getWorkspaceId(), hook.getConnectionId());
            } catch (RuntimeException ex) {
                LoggerFactory.getLogger(IntegrationWorker.class).warn("Webhook 通知调度失败，将在后续周期重试");
            }
        }
    }

    private void process(
            IntegrationRun run,
            IntegrationConnection c,
            RepositoryProvider provider,
            String token,
            IntegrationProgress progress) {
        if ("ISSUE_REFRESH".equals(run.getKind())) {
            issueTracker.trackTargets(run, c, provider, token);
        } else if ("IMPORT".equals(run.getKind())) {
            imports(run);
        } else {
            if (("DISCOVER".equals(run.getKind()) || c.isAutoImport())
                    && !progress.isDiscoveryDone()) {
                discover(run, c, provider, token, progress);
            }
            if ("SYNC".equals(run.getKind())) {
                if (!progress.isSyncDone()) {
                    sync(run, c, provider, token, progress);
                }
                issueTracker.track(run, c, provider, token);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    @XxlJob("integrationScheduleJob")
    public void schedule() {
        for (IntegrationConnection c : mapper.dueConnections()) {
            state.schedule(c.getWorkspaceId(), c.getId());
        }
    }

    private void discover(
            IntegrationRun run,
            IntegrationConnection c,
            RepositoryProvider p,
            String token,
            IntegrationProgress progress) {
        for (int page = progress.getDiscoveryPage(); page <= properties.maxPages(); page++) {
            state.heartbeat(run);
            List<ExternalRepository> rows = p.repositories(c, token, page);
            progress.setDiscoveryPage(page + 1);
            progress.setDiscoveryDone(rows.size() < 100);
            progress.store(run);
            state.saveRepositories(run, rows);
            if (rows.size() < 100) {
                return;
            }
        }
        throw new BusinessException("仓库发现超过分页上限，本次结果不完整，请缩小范围或调整部署上限");
    }

    private void imports(IntegrationRun run) {
        long offset = 0;
        List<IntegrationRunItem> items;
        do {
            items = mapper.items(run.getWorkspaceId(), run.getId(), offset, 100);
            for (var item : items) {
                if ("SUCCEEDED".equals(item.getStatus()) || "FAILED".equals(item.getStatus())) {
                    continue;
                }
                try {
                    state.importItem(run, item);
                } catch (RuntimeException ex) {
                    state.failedItem(run, item, safeError(ex));
                }
            }
            offset += items.size();
        } while (items.size() == 100);
    }

    private void sync(
            IntegrationRun run,
            IntegrationConnection c,
            RepositoryProvider p,
            String token,
            IntegrationProgress progress) {
        long offset = 0;
        List<ExternalRepository> repositories;
        do {
            repositories = mapper.repositories(c.getWorkspaceId(), c.getId(), -1L, offset, 100);
            for (var stored : repositories) {
                if (stored.getId() <= progress.getRepositoryCursor()) {
                    continue;
                }
                syncRepository(run, c, p, token, progress, stored);
            }
            offset += repositories.size();
        } while (repositories.size() == 100);
        progress.setSyncDone(true);
        progress.store(run);
        state.checkpoint(run);
    }

    private void syncRepository(
            IntegrationRun run,
            IntegrationConnection c,
            RepositoryProvider p,
            String token,
            IntegrationProgress progress,
            ExternalRepository stored) {
        state.heartbeat(run);
        try {
            ExternalRepository fresh = progress.getRepository();
            if (fresh == null || !fresh.getId().equals(stored.getId())) {
                progress.nextRepository(progress.getRepositoryCursor());
                fresh = p.repository(c, token, stored);
                fresh.setId(stored.getId());
                progress.setRepository(fresh);
                progress.store(run);
                state.refreshRepository(run, fresh);
            }
            while (progress.getKindIndex() < DevelopmentKind.values().length) {
                DevelopmentKind kind = DevelopmentKind.values()[progress.getKindIndex()];
                development(run, c, p, token, fresh, kind, progress);
            }
        } catch (IntegrationConflictException | ExternalApiDeferredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            state.repositoryError(run, stored.getId(), safeError(ex));
        }
        progress.nextRepository(stored.getId());
        progress.store(run);
        state.checkpoint(run);
    }

    private void development(
            IntegrationRun run,
            IntegrationConnection c,
            RepositoryProvider provider,
            String token,
            ExternalRepository repository,
            DevelopmentKind kind,
            IntegrationProgress progress) {
        if (progress.getSince() == null) {
            progress.setSince(
                    LocalDateTime.now(ZoneId.systemDefault())
                            .minusDays(properties.recentDays())
                            .toString());
        }
        LocalDateTime since = LocalDateTime.parse(progress.getSince());
        DevelopmentSnapshot snapshot = new DevelopmentSnapshot();
        snapshot.setKind(kind.name());
        snapshot.setSince(since);
        List<DevelopmentItem> collected = new ArrayList<>(progress.getItems());
        try {
            boolean finished = false;
            for (int page = progress.getPage();
                    page <= properties.maxPages() && !finished;
                    page++) {
                state.heartbeat(run);
                List<DevelopmentItem> rows =
                        provider.development(c, token, repository, kind, since, page);
                boolean pastWindow = collectRecent(rows, kind, since, collected);
                finished = pageFinished(snapshot, rows.size(), pastWindow, collected, page);
                if (!finished) {
                    progress.setPage(page + 1);
                    progress.setItems(collected);
                    progress.store(run);
                    state.checkpoint(run);
                }
            }
            snapshot.setAvailable(true);
            snapshot.setCollected(collected.size());
            snapshot.setOpenCount(
                    (int) collected.stream().filter(i -> "OPEN".equals(i.getStatus())).count());
        } catch (IntegrationConflictException | ExternalApiDeferredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            snapshot.setAvailable(false);
            snapshot.setMessage(safeError(ex));
            collected = List.of();
            state.repositoryError(run, repository.getId(), snapshot.getMessage());
        }
        progress.nextKind();
        progress.store(run);
        state.snapshot(run, repository, snapshot, collected);
    }

    private boolean pageFinished(
            DevelopmentSnapshot snapshot,
            int pageSize,
            boolean pastWindow,
            List<DevelopmentItem> collected,
            int page) {
        if (collected.size() > properties.maxItems()) {
            collected.subList(properties.maxItems(), collected.size()).clear();
            snapshot.setTruncated(true);
            return true;
        }
        if (pageSize < 100 || pastWindow) {
            return true;
        }
        if (page == properties.maxPages()) {
            snapshot.setTruncated(true);
            return true;
        }
        return false;
    }

    private boolean collectRecent(
            List<DevelopmentItem> rows,
            DevelopmentKind kind,
            LocalDateTime since,
            List<DevelopmentItem> collected) {
        Set<String> seen =
                collected.stream()
                        .map(DevelopmentItem::getExternalId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        collected.addAll(
                rows.stream()
                        .filter(item -> !beforeWindow(item, since))
                        .filter(item -> kind.name().equals(item.getKind()))
                        .filter(
                                item ->
                                        item.getExternalId() == null
                                                || seen.add(item.getExternalId()))
                        .toList());
        return rows.stream().anyMatch(item -> beforeWindow(item, since));
    }

    private boolean beforeWindow(DevelopmentItem item, LocalDateTime since) {
        return item.getSourceUpdatedAt() != null && item.getSourceUpdatedAt().isBefore(since);
    }

    static String safeError(RuntimeException ex) {
        return ex instanceof BusinessException ? ex.getMessage() : "同步失败，请检查连接配置或联系管理员";
    }
}
