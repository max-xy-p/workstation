package com.mike.workstation.service;

import com.mike.workstation.config.InvitationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Backend 使用专用内部凭据提交邮件，不持有 Resend 密钥。 */
@Component
public class InvitationMailClient {
    private final InvitationProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    public InvitationMailClient(InvitationProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public record MailRequest(String requestKey, String to, String subject, String text,
            OffsetDateTime expiresAt) {}

    public void enqueue(MailRequest request) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.mailUrl()
                .replaceAll("(?<!/)/++$", "") + "/internal/mail/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.mailToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(request))).build();
        HttpResponse<Void> response = http.send(httpRequest, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 202) {
            throw new IllegalStateException("邮件服务暂未接收邀请");
        }
    }
}
