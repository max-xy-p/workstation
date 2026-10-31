package com.mike.workstation.keycloak.mail;

import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class WorkstationEmailSender implements EmailSenderProvider {
    static final String CODE_EXPIRES_AT = "workstation.code.expires-at";
    private final HttpClient client;
    private final URI endpoint;
    private final String token;
    private final Clock clock;
    private final long lifetime;

    WorkstationEmailSender(
            HttpClient client, URI endpoint, String token, Clock clock, long lifetime) {
        this.client = client;
        this.endpoint = endpoint;
        this.token = token;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    @Override
    public void send(
            Map<String, String> config,
            String address,
            String subject,
            String textBody,
            String htmlBody)
            throws EmailException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestKey", UUID.randomUUID().toString());
        body.put("to", address);
        body.put("subject", subject);
        body.put("text", textBody);
        body.put("html", htmlBody);
        Instant expiresAt = clock.instant().plusSeconds(lifetime);
        String codeExpiry = config.get(CODE_EXPIRES_AT);
        if (codeExpiry != null) {
            Instant requestedExpiry = Instant.parse(codeExpiry);
            if (requestedExpiry.isBefore(expiresAt)) {
                expiresAt = requestedExpiry;
            }
        }
        body.put("expiresAt", expiresAt.toString());
        try {
            // 同一次提交的网络重试复用完全相同的请求标识、正文和到期时间。
            HttpRequest request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            JsonSerialization.writeValueAsString(body)))
                            .build();
            if (submit(request) != 202) {
                throw new EmailException("邮件服务未接收邮件，请稍后重试");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new EmailException("邮件提交已中断");
        } catch (IOException | RuntimeException error) {
            // 不传播包含验证链接、邮箱和凭据的下游响应或异常。
            throw new EmailException("邮件服务暂时不可用");
        }
    }

    private int submit(HttpRequest request) throws IOException, InterruptedException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException error) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @Override
    public void validate(Map<String, String> config) throws EmailException {
        if (token == null || token.length() < 32) {
            throw new EmailException("未配置邮件服务身份凭据");
        }
    }

    @Override
    public void close() {
        // HttpClient 由工厂共享，并在工厂关闭时统一释放。
    }
}
