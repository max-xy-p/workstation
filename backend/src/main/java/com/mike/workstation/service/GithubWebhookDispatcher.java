package com.mike.workstation.service;

import com.mike.workstation.domain.GithubWebhook;
import com.mike.workstation.domain.GithubWebhookDelivery;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IntegrationRun;
import com.mike.workstation.domain.IssueSource;
import com.mike.workstation.integration.WebhookSignature;
import com.mike.workstation.mapper.GithubWebhookMapper;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.mapper.IssueSourceMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** 同一来源通知合并成现有队列的目标作业；接收与执行状态都可在重启后恢复。 */
@Service
public class GithubWebhookDispatcher {
    private final GithubWebhookMapper hooks;
    private final IntegrationMapper integrations;
    private final IssueSourceMapper sources;
    private final IntegrationStateService state;

    public GithubWebhookDispatcher(
            GithubWebhookMapper hooks,
            IntegrationMapper integrations,
            IssueSourceMapper sources,
            IntegrationStateService state) {
        this.hooks = hooks;
        this.integrations = integrations;
        this.sources = sources;
        this.state = state;
    }

    public List<GithubWebhook> pending() {
        return hooks.pendingConnections();
    }

    /** 收敛已结束的作业，并将到期通知合并为持久化目标；执行期间的新通知留给下一轮。 */
    @Transactional
    public void dispatch(Long w, Long c) {
        IntegrationConnection connection = integrations.lockConnection(w, c);
        GithubWebhook hook = hooks.lock(w, c);
        if (hook == null) {
            return;
        }
        settle(w, c);
        if (connection == null
                || connection.isDeleted()
                || !connection.isEnabled()
                || !hook.isEnabled()
                || !state.canManage(connection)) {
            hooks.cancelRuns(w, c);
            hooks.cancelDeliveries(w, c);
            return;
        }
        if (integrations.active(w, c) != null) {
            return;
        }
        // 到期的整批校准优先入队，持续通知不能饿死遗漏检查。
        if (connection.getScheduleMinutes() > 0
                && connection.getNextSyncAt() != null
                && !connection.getNextSyncAt().isAfter(LocalDateTime.now(ZoneId.systemDefault()))) {
            return;
        }
        List<GithubWebhookDelivery> deliveries = hooks.pending(w, c);
        IntegrationRun run = null;
        for (GithubWebhookDelivery d : deliveries) {
            IssueSource source =
                    d.getSourceId() == null ? null : sources.tracked(w, d.getSourceId());
            if (d.getWebhookVersion() != hook.getVersion()
                    || source == null
                    || !source.getLinkToken().equals(d.getLinkToken())
                    || "STOPPED".equals(source.getTrackingStatus())) {
                d.setStatus("IGNORED");
                d.setLastError("Webhook 配置或来源关联已变化");
                hooks.update(d);
                continue;
            }
            if (run == null) {
                run = newRun(connection, hook);
                integrations.insertRun(run);
            }
            hooks.target(w, run.getId(), source);
            d.setRunId(run.getId());
            d.setStatus("QUEUED");
            d.setAttempts(d.getAttempts() + 1);
            d.setLastError(null);
            hooks.update(d);
        }
    }

    private IntegrationRun newRun(IntegrationConnection c, GithubWebhook hook) {
        IntegrationRun run = new IntegrationRun();
        run.setWorkspaceId(c.getWorkspaceId());
        run.setConnectionId(c.getId());
        run.setKind("ISSUE_REFRESH");
        run.setStatus("QUEUED");
        run.setActorSubject(c.getOwnerSubject());
        run.setConfigVersion(c.getVersion());
        run.setWebhookId(hook.getId());
        run.setWebhookVersion(hook.getVersion());
        run.setRequestKey("webhook-" + UUID.randomUUID());
        run.setRequestHash(
                WebhookSignature.hash(run.getRequestKey().getBytes(StandardCharsets.UTF_8)));
        run.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return run;
    }

    /** 由持久化目标结果推进投递状态；重试只覆盖已经成功写入收件箱的通知。 */
    private void settle(Long w, Long c) {
        for (GithubWebhookDelivery d : hooks.settled(w, c)) {
            if ("CANCELLED".equals(d.getRunStatus())) {
                d.setStatus("CANCELLED");
                d.setLastError("连接、权限或 Webhook 配置已变化");
            } else if ("SUCCEEDED".equals(d.getTargetStatus())) {
                d.setStatus("SUCCEEDED");
                d.setLastError(null);
            } else if ("CANCELLED".equals(d.getTargetStatus())) {
                d.setStatus("IGNORED");
                d.setLastError("来源关联已变化");
            } else {
                d.setStatus(d.getAttempts() < 3 ? "RETRY" : "FAILED");
                d.setLastError(
                        d.getTargetError() == null ? "指定来源刷新失败，请检查执行记录" : d.getTargetError());
                d.setNextAttemptAt(
                        LocalDateTime.now(ZoneId.systemDefault())
                                .plusSeconds(30L << Math.min(d.getAttempts(), 3)));
            }
            hooks.update(d);
        }
    }
}
