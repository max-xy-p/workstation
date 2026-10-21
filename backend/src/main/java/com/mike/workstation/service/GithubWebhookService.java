package com.mike.workstation.service;

import com.mike.workstation.assembler.ApiTime;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.exception.*;
import com.mike.workstation.integration.*;
import com.mike.workstation.mapper.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 配置、验签与持久化接收的短事务，不在接收线程请求 GitHub。 */
@Service
public class GithubWebhookService {
    public static final int MAX_BODY_BYTES=1_000_000;
    private final GithubWebhookMapper hooks;
    private final IntegrationMapper integrations;
    private final IssueSourceMapper sources;
    private final IntegrationStateService state;
    private final WorkspaceAuthorizationService authorization;
    private final CredentialCipher cipher;
    private final IntegrationProperties properties;
    private final AuditService audit;
    private final ObjectMapper json;
    public GithubWebhookService(GithubWebhookMapper hooks,IntegrationMapper integrations,IssueSourceMapper sources,
            IntegrationStateService state,WorkspaceAuthorizationService authorization,CredentialCipher cipher,
            IntegrationProperties properties,AuditService audit,ObjectMapper json) {
        this.hooks=hooks;this.integrations=integrations;this.sources=sources;this.state=state;this.authorization=authorization;
        this.cipher=cipher;this.properties=properties;this.audit=audit;this.json=json;
    }
    private IntegrationConnection manage(Long w,Long c) {
        authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE);
        IntegrationConnection connection=integrations.connection(w,c);
        RequestChecks.requiredExisting(connection,"数据源连接不存在");
        if(!"GITHUB".equals(connection.getProvider())) throw new BusinessException("Webhook 首期仅支持 GitHub");
        return connection;
    }
    public GithubWebhookResponse get(Long w,Long c) { manage(w,c);return response(hooks.get(w,c)); }
    private GithubWebhookResponse response(GithubWebhook hook) {
        return hook==null?new GithubWebhookResponse(false,false,0,null):
                new GithubWebhookResponse(true,hook.isEnabled(),hook.getVersion(),"/api/hooks/github/"+hook.getId());
    }
    @Transactional
    public GithubWebhookResponse configure(Long w,Long c,GithubWebhookRequest request) {
        manage(w,c);
        if(!properties.enabled()) throw new BusinessException("数据源功能未启用");
        IntegrationConnection connection=integrations.lockConnection(w,c);
        if(connection==null || connection.isDeleted() || !state.canManage(connection)) throw new BusinessException("请先由管理员接管有效的连接凭证");
        GithubWebhook hook=hooks.lock(w,c);
        int version=hook==null?0:hook.getVersion();
        if(request.enabled()==null || request.expectedVersion()==null || request.expectedVersion()!=version)
            throw new IntegrationConflictException("Webhook 配置已变化，请刷新后重试");
        if(hook==null) { hook=new GithubWebhook();hook.setId(UUID.randomUUID().toString());hook.setWorkspaceId(w);hook.setConnectionId(c); }
        if(request.secret()!=null && !request.secret().isBlank()) {
            if(request.secret().length()<32 || request.secret().length()>256) throw new BusinessException("Webhook Secret 长度应为 32 到 256 个字符");
            hook.setSecret(cipher.encrypt(request.secret(),context(hook)));hook.setKeyVersion(properties.keyVersion());
        }
        if(hook.getSecret()==null) throw new BusinessException("首次配置必须提供 Webhook Secret");
        hooks.cancelRuns(w,c);hooks.cancelDeliveries(w,c);
        hook.setEnabled(request.enabled());hook.setVersion(version+1);hooks.save(hook);
        audit.success(w,"WEBHOOK_CONFIGURE","INTEGRATION",c);
        return response(hook);
    }
    public static String context(GithubWebhook hook) { return "webhook:"+hook.getWorkspaceId()+":"+hook.getConnectionId()+":"+hook.getId(); }
    @Transactional
    public void receive(String endpoint,String deliveryKey,String event,String signature,byte[] body) {
        if(!properties.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"数据源功能未启用");
        if(body.length>MAX_BODY_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Webhook 原文过大");
        GithubWebhook route=hooks.endpoint(endpoint);
        if(route==null) throw new NotFoundException("Webhook 不存在");
        IntegrationConnection connection=integrations.lockConnection(route.getWorkspaceId(),route.getConnectionId());
        GithubWebhook hook=hooks.lock(route.getWorkspaceId(),route.getConnectionId());
        if(hook==null || !hook.isEnabled() || connection==null || connection.isDeleted() || !connection.isEnabled() || !state.canManage(connection))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Webhook 不可用");
        String secret=cipher.decrypt(hook.getSecret(),hook.getKeyVersion(),context(hook));
        if(!WebhookSignature.valid(secret,body,signature)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Webhook 签名不合法");
        if(deliveryKey==null || !deliveryKey.matches("[A-Za-z0-9-]{1,80}") || event==null || !event.matches("[a-z_]{1,32}"))
            throw new BusinessException("Webhook 投递标识或事件类型不合法");
        String hash=WebhookSignature.hash(body);
        GithubWebhookDelivery existing=hooks.duplicate(hook.getId(),deliveryKey);
        if(existing!=null) {
            if(!hash.equals(existing.getBodyHash()) || !event.equals(existing.getEventType()))
                throw new IntegrationConflictException("同一投递标识不能提交不同事件内容");
            return;
        }
        JsonNode payload;
        try { payload=json.readTree(body); }
        catch(RuntimeException ex) { throw new BusinessException("Webhook JSON 不合法"); }
        if(payload==null || !payload.isObject()) throw new BusinessException("Webhook JSON 必须为对象");
        GithubWebhookDelivery delivery=new GithubWebhookDelivery();delivery.setWebhookId(hook.getId());delivery.setWorkspaceId(hook.getWorkspaceId());
        delivery.setConnectionId(hook.getConnectionId());delivery.setWebhookVersion(hook.getVersion());delivery.setDeliveryKey(deliveryKey);
        delivery.setBodyHash(hash);delivery.setEventType(event);delivery.setStatus("IGNORED");
        delivery.setReceivedAt(LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));delivery.setNextAttemptAt(delivery.getReceivedAt());
        if("issues".equals(event)) select(delivery,payload);
        hooks.insert(delivery);
    }
    private void select(GithubWebhookDelivery delivery,JsonNode payload) {
        String action=payload.path("action").asText("");
        if(action.isBlank() || action.length()>64 || !payload.path("repository").path("id").isIntegralNumber()
                || !payload.path("issue").path("id").isIntegralNumber() || !payload.path("issue").path("number").isIntegralNumber())
            throw new BusinessException("Issue 事件缺少有效身份或动作");
        delivery.setAction(action);
        IssueSource source=sources.external(delivery.getWorkspaceId(),delivery.getConnectionId(),payload.path("repository").path("id").asText(),payload.path("issue").path("id").asText());
        if(source==null || !source.getIssueNumber().equals(payload.path("issue").path("number").asLong()) || "STOPPED".equals(source.getTrackingStatus())) return;
        delivery.setSourceId(source.getId());delivery.setLinkToken(source.getLinkToken());delivery.setStatus("RECEIVED");
    }
    public PageResponse<GithubWebhookDeliveryResponse> deliveries(Long w,Long c,int page,int size) {
        manage(w,c);PageQuery q=PageQuery.of(page,size);
        return PageResponse.of(hooks.list(w,c,q.offset(),q.size()).stream().map(GithubWebhookService::deliveryResponse).toList(),page,size,hooks.count(w,c));
    }
    public static GithubWebhookDeliveryResponse deliveryResponse(GithubWebhookDelivery d) {
        return new GithubWebhookDeliveryResponse(d.getId(),d.getDeliveryKey(),d.getEventType(),d.getAction(),d.getStatus(),d.getAttempts(),d.getRunId(),d.getLastError(),ApiTime.toOffset(d.getReceivedAt()));
    }
    @Transactional
    public GithubWebhookDeliveryResponse retry(Long w,Long c,Long id) {
        manage(w,c);
        IntegrationConnection connection=integrations.lockConnection(w,c);
        GithubWebhook hook=hooks.lock(w,c);
        if(hook==null || !hook.isEnabled() || !connection.isEnabled() || !state.canManage(connection)) throw new BusinessException("Webhook 或连接不可用");
        GithubWebhookDelivery d=hooks.delivery(w,c,id);RequestChecks.requiredExisting(d,"投递记录不存在");
        if(!"FAILED".equals(d.getStatus()) && !"CANCELLED".equals(d.getStatus())) throw new BusinessException("只有失败或取消的投递可以重试");
        IssueSource current=d.getSourceId()==null?null:sources.tracked(w,d.getSourceId());
        if(current==null || !current.getLinkToken().equals(d.getLinkToken()) || "STOPPED".equals(current.getTrackingStatus()))
            throw new IntegrationConflictException("来源关联已变化，不能重试旧通知");
        d.setWebhookVersion(hook.getVersion());d.setStatus("RECEIVED");d.setAttempts(0);d.setRunId(null);d.setLastError(null);d.setNextAttemptAt(LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        hooks.update(d);audit.success(w,"WEBHOOK_RETRY","INTEGRATION",c);return deliveryResponse(d);
    }
}
