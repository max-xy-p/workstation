package com.mike.workstation.service;

import com.mike.workstation.assembler.ApiTime;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.GithubWebhook;
import com.mike.workstation.domain.GithubWebhookDelivery;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IssueSource;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.GithubWebhookDeliveryResponse;
import com.mike.workstation.dto.GithubWebhookRequest;
import com.mike.workstation.dto.GithubWebhookResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.IntegrationConflictException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.integration.CredentialCipher;
import com.mike.workstation.integration.WebhookSignature;
import com.mike.workstation.mapper.GithubWebhookMapper;
import com.mike.workstation.mapper.IntegrationMapper;
import com.mike.workstation.mapper.IssueSourceMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 配置、验签与持久化接收的短事务，不在接收线程请求 GitHub。 */
@Service
public class GithubWebhookService {
    public static final int MAX_BODY_BYTES = 1_000_000;
    private final GithubWebhookMapper hooks;
    private final IntegrationMapper integrations;
    private final IssueSourceMapper sources;
    private final IntegrationStateService state;
    private final WorkspaceAuthorizationService authorization;
    private final CredentialCipher cipher;
    private final IntegrationProperties properties;
    private final AuditService audit;
    private final ObjectMapper json;

    public GithubWebhookService(
            GithubWebhookMapper hooks,
            IntegrationMapper integrations,
            IssueSourceMapper sources,
            IntegrationStateService state,
            WorkspaceAuthorizationService authorization,
            CredentialCipher cipher,
            IntegrationProperties properties,
            AuditService audit,
            ObjectMapper json) {
        this.hooks = hooks;
        this.integrations = integrations;
        this.sources = sources;
        this.state = state;
        this.authorization = authorization;
        this.cipher = cipher;
        this.properties = properties;
        this.audit = audit;
        this.json = json;
    }

    private IntegrationConnection manage(Long w, Long c) {
        authorization.require(w, WorkspacePermission.INTEGRATION_MANAGE);
        IntegrationConnection connection = integrations.connection(w, c);
        RequestChecks.requiredExisting(connection, "数据源连接不存在");
        if (!"GITHUB".equals(connection.getProvider())) {
            throw new BusinessException("Webhook 首期仅支持 GitHub");
        }
        return connection;
    }

    public GithubWebhookResponse get(Long w, Long c) {
        manage(w, c);
        return response(hooks.get(w, c));
    }

    private GithubWebhookResponse response(GithubWebhook hook) {
        return hook == null
                ? new GithubWebhookResponse(false, false, 0, null)
                : new GithubWebhookResponse(
                        true,
                        hook.isEnabled(),
                        hook.getVersion(),
                        "/api/hooks/github/" + hook.getId());
    }

    @Transactional
    public GithubWebhookResponse configure(Long w, Long c, GithubWebhookRequest request) {
        manage(w, c);
        if (!properties.enabled()) {
            throw new BusinessException("数据源功能未启用");
        }
        IntegrationConnection connection = integrations.lockConnection(w, c);
        if (connection == null || connection.isDeleted() || !state.canManage(connection)) {
            throw new BusinessException("请先由管理员接管有效的连接凭证");
        }
        GithubWebhook hook = hooks.lock(w, c);
        int version = hook == null ? 0 : hook.getVersion();
        if (request.enabled() == null
                || request.expectedVersion() == null
                || request.expectedVersion() != version) {
            throw new IntegrationConflictException("Webhook 配置已变化，请刷新后重试");
        }
        if (hook == null) {
            hook = new GithubWebhook();
            hook.setId(UUID.randomUUID().toString());
            hook.setWorkspaceId(w);
            hook.setConnectionId(c);
        }
        if (request.secret() != null && !request.secret().isBlank()) {
            if (request.secret().length() < 32 || request.secret().length() > 256) {
                throw new BusinessException("Webhook Secret 长度应为 32 到 256 个字符");
            }
            hook.setSecret(cipher.encrypt(request.secret(), context(hook)));
            hook.setKeyVersion(properties.keyVersion());
        }
        if (hook.getSecret() == null) {
            throw new BusinessException("首次配置必须提供 Webhook Secret");
        }
        // 配置轮换使旧代次失效，已经排队的事件不能继续使用新密钥对应的配置执行。
        hooks.cancelRuns(w, c);
        hooks.cancelDeliveries(w, c);
        hook.setEnabled(request.enabled());
        hook.setVersion(version + 1);
        hooks.save(hook);
        audit.success(w, "WEBHOOK_CONFIGURE", "INTEGRATION", c);
        return response(hook);
    }

    /** 将密文绑定到具体空间、连接和端点，避免密钥密文被移用到其他配置。 */
    public static String context(GithubWebhook hook) {
        return "webhook:"
                + hook.getWorkspaceId()
                + ":"
                + hook.getConnectionId()
                + ":"
                + hook.getId();
    }

    /** 只验签并持久化收件记录；提交成功后才能响应接收成功，平台读取由后台完成。 */
    @Transactional
    public void receive(
            String endpoint, String deliveryKey, String event, String signature, byte[] body) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "数据源功能未启用");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook 原文过大");
        }
        GithubWebhook route = hooks.endpoint(endpoint);
        if (route == null) {
            throw new NotFoundException("Webhook 不存在");
        }
        IntegrationConnection connection =
                integrations.lockConnection(route.getWorkspaceId(), route.getConnectionId());
        GithubWebhook hook = hooks.lock(route.getWorkspaceId(), route.getConnectionId());
        if (hook == null
                || !hook.isEnabled()
                || connection == null
                || connection.isDeleted()
                || !connection.isEnabled()
                || !state.canManage(connection)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Webhook 不可用");
        }
        String secret = cipher.decrypt(hook.getSecret(), hook.getKeyVersion(), context(hook));
        if (!WebhookSignature.valid(secret, body, signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Webhook 签名不合法");
        }
        if (deliveryKey == null
                || !deliveryKey.matches("[A-Za-z0-9-]{1,80}")
                || event == null
                || !event.matches("[a-z_]{1,32}")) {
            throw new BusinessException("Webhook 投递标识或事件类型不合法");
        }
        // 同一投递标识只有在正文摘要与事件类型都相同时，才可视为安全的重复投递。
        String hash = WebhookSignature.hash(body);
        GithubWebhookDelivery existing = hooks.duplicate(hook.getId(), deliveryKey);
        if (existing != null) {
            if (!hash.equals(existing.getBodyHash()) || !event.equals(existing.getEventType())) {
                throw new IntegrationConflictException("同一投递标识不能提交不同事件内容");
            }
            return;
        }
        JsonNode payload;
        try {
            payload = json.readTree(body);
        } catch (RuntimeException ex) {
            throw new BusinessException("Webhook JSON 不合法");
        }
        if (payload == null || !payload.isObject()) {
            throw new BusinessException("Webhook JSON 必须为对象");
        }
        GithubWebhookDelivery delivery = new GithubWebhookDelivery();
        delivery.setWebhookId(hook.getId());
        delivery.setWorkspaceId(hook.getWorkspaceId());
        delivery.setConnectionId(hook.getConnectionId());
        delivery.setWebhookVersion(hook.getVersion());
        delivery.setDeliveryKey(deliveryKey);
        delivery.setBodyHash(hash);
        delivery.setEventType(event);
        delivery.setStatus("IGNORED");
        // MySQL DATETIME 以秒保存，避免小数秒舍入把首次调度时间意外推迟到下一秒。
        delivery.setReceivedAt(
                LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS));
        delivery.setNextAttemptAt(delivery.getReceivedAt());
        if ("issues".equals(event)) {
            select(delivery, payload);
        }
        hooks.insert(delivery);
    }

    private void select(GithubWebhookDelivery delivery, JsonNode payload) {
        String action = payload.path("action").asText("");
        if (action.isBlank()
                || action.length() > 64
                || !payload.path("repository").path("id").isIntegralNumber()
                || !payload.path("issue").path("id").isIntegralNumber()
                || !payload.path("issue").path("number").isIntegralNumber()) {
            throw new BusinessException("Issue 事件缺少有效身份或动作");
        }
        delivery.setAction(action);
        IssueSource source =
                sources.external(
                        delivery.getWorkspaceId(),
                        delivery.getConnectionId(),
                        payload.path("repository").path("id").asText(),
                        payload.path("issue").path("id").asText());
        if (source == null
                || !source.getIssueNumber().equals(payload.path("issue").path("number").asLong())
                || "STOPPED".equals(source.getTrackingStatus())) {
            return;
        }
        delivery.setSourceId(source.getId());
        delivery.setLinkToken(source.getLinkToken());
        delivery.setStatus("RECEIVED");
    }

    public PageResponse<GithubWebhookDeliveryResponse> deliveries(
            Long w, Long c, int page, int size) {
        manage(w, c);
        PageQuery q = PageQuery.of(page, size);
        return PageResponse.of(
                hooks.list(w, c, q.offset(), q.size()).stream()
                        .map(GithubWebhookService::deliveryResponse)
                        .toList(),
                page,
                size,
                hooks.count(w, c));
    }

    public static GithubWebhookDeliveryResponse deliveryResponse(GithubWebhookDelivery d) {
        return new GithubWebhookDeliveryResponse(
                d.getId(),
                d.getDeliveryKey(),
                d.getEventType(),
                d.getAction(),
                d.getStatus(),
                d.getAttempts(),
                d.getRunId(),
                d.getLastError(),
                ApiTime.toOffset(d.getReceivedAt()));
    }

    /** 显式重试仍属于原关联代次，不能借此恢复已经解除或替换的任务来源关系。 */
    @Transactional
    public GithubWebhookDeliveryResponse retry(Long w, Long c, Long id) {
        manage(w, c);
        IntegrationConnection connection = integrations.lockConnection(w, c);
        GithubWebhook hook = hooks.lock(w, c);
        if (hook == null
                || !hook.isEnabled()
                || !connection.isEnabled()
                || !state.canManage(connection)) {
            throw new BusinessException("Webhook 或连接不可用");
        }
        GithubWebhookDelivery d = hooks.delivery(w, c, id);
        RequestChecks.requiredExisting(d, "投递记录不存在");
        if (!"FAILED".equals(d.getStatus()) && !"CANCELLED".equals(d.getStatus())) {
            throw new BusinessException("只有失败或取消的投递可以重试");
        }
        IssueSource current = d.getSourceId() == null ? null : sources.tracked(w, d.getSourceId());
        if (current == null
                || !current.getLinkToken().equals(d.getLinkToken())
                || "STOPPED".equals(current.getTrackingStatus())) {
            throw new IntegrationConflictException("来源关联已变化，不能重试旧通知");
        }
        d.setWebhookVersion(hook.getVersion());
        d.setStatus("RECEIVED");
        d.setAttempts(0);
        d.setRunId(null);
        d.setLastError(null);
        d.setNextAttemptAt(
                LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS));
        hooks.update(d);
        audit.success(w, "WEBHOOK_RETRY", "INTEGRATION", c);
        return deliveryResponse(d);
    }
}
