package com.mike.workstation.mail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ResendClient {
    private final MailProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;
    private final URI endpoint;

    @Autowired
    public ResendClient(MailProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.resend.com/emails"));
    }

    ResendClient(MailProperties properties, ObjectMapper json, HttpClient client, URI endpoint) {
        this.properties = properties;
        this.json = json;
        this.client = client;
        this.endpoint = endpoint;
    }

    public Result send(String taskId, MailRequest mail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", properties.fromName() + " <" + properties.from() + ">");
        body.put("to", List.of(mail.to()));
        body.put("subject", mail.subject());
        if (mail.text() != null) {
            body.put("text", mail.text());
        }
        if (mail.html() != null) {
            body.put("html", mail.html());
        }
        if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
            body.put("reply_to", properties.replyTo());
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + properties.resendApiKey())
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "workstation-mail/" + taskId)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return interpret(response);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Result(null, true, "PROVIDER_INTERRUPTED", 60);
        } catch (IOException | RuntimeException error) {
            return new Result(null, true, "PROVIDER_UNAVAILABLE", 60);
        }
    }

    private Result interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        JsonNode body;
        try {
            body = json.readTree(response.body());
        } catch (RuntimeException error) {
            return new Result(null, true, "PROVIDER_INVALID_RESPONSE", 60);
        }
        if (status >= 200 && status < 300) {
            String id = body.path("id").asText("");
            return id.isBlank() || id.length() > 128
                    ? new Result(null, true, "PROVIDER_INVALID_RESPONSE", 60)
                    : new Result(id, false, null, 0);
        }
        boolean concurrent = status == 409 && "concurrent_idempotent_requests".equals(body.path("name").asText());
        boolean retryable = status == 429 || status >= 500 || concurrent;
        long delay = 60;
        try {
            delay = Math.max(1, Math.min(86400, Long.parseLong(response.headers().firstValue("retry-after").orElse("60"))));
        } catch (NumberFormatException ignored) {
            // 无法解析时使用保守退避，避免立即重试压垮邮件供应商。
        }
        return new Result(null, retryable, "PROVIDER_HTTP_" + status, delay);
    }

    public record Result(String providerId, boolean retryable, String errorCode, long retryAfterSeconds) { }
}
