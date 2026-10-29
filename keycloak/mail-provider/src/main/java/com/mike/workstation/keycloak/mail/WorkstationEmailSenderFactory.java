package com.mike.workstation.keycloak.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.keycloak.Config;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.email.EmailSenderProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class WorkstationEmailSenderFactory implements EmailSenderProviderFactory {
    private HttpClient client;
    private URI endpoint;
    private String token;
    private long lifetime;

    @Override
    public EmailSenderProvider create(KeycloakSession session) {
        // 投递期限不长于该 Realm 的用户操作链接期限，过期任务不会继续重试。
        long ttl = lifetime;
        if (session.getContext().getRealm() != null) {
            ttl = Math.min(ttl, session.getContext().getRealm().getActionTokenGeneratedByUserLifespan());
        }
        return new WorkstationEmailSender(client, endpoint, token, Clock.systemUTC(), Math.max(1, ttl));
    }

    @Override
    public void init(Config.Scope config) {
        configure(System.getenv().getOrDefault("MAIL_SERVICE_URL", "http://mail-service:8082"),
                System.getenv("MAIL_KEYCLOAK_TOKEN"));
    }

    void configure(String url, String credential) {
        endpoint = endpoint(url);
        token = credential;
        if (token == null || token.length() < 32) {
            throw new IllegalArgumentException("必须配置至少 32 字符的邮件调用凭据");
        }
        lifetime = 900;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static URI endpoint(String url) {
        URI base = URI.create(url);
        boolean local = "mail-service".equals(base.getHost()) || "localhost".equals(base.getHost())
                || "127.0.0.1".equals(base.getHost());
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath()))
                || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && local))) {
            throw new IllegalArgumentException("邮件服务地址必须为 HTTPS，仅容器服务和本机允许 HTTP");
        }
        return base.resolve("/internal/mail/messages");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) { }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public String getId() {
        return "workstation-mail";
    }
}
