package com.mike.workstation.mail;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ResendWebhookController {
    private static final Map<String, String> STATES = Map.of(
            "email.delivered", "DELIVERED", "email.delivery_delayed", "DELAYED",
            "email.bounced", "BOUNCED", "email.complained", "COMPLAINED", "email.failed", "FAILED");
    private final MailProperties properties;
    private final MailTaskRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public ResendWebhookController(MailProperties properties, MailTaskRepository repository, ObjectMapper json, Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping("/hooks/resend")
    public Map<String, String> receive(HttpServletRequest request) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未启用邮件投递回调");
        }
        try {
            byte[] raw = request.getInputStream().readNBytes(100001);
            if (raw.length > 100000) {
                throw new IllegalArgumentException();
            }
            String id = request.getHeader("svix-id");
            String timestamp = request.getHeader("svix-timestamp");
            String signature = request.getHeader("svix-signature");
            verify(id, timestamp, signature, raw);
            JsonNode event = json.readTree(raw);
            String state = STATES.get(event.path("type").asText());
            if (state != null) {
                String providerId = event.path("data").path("email_id").asText();
                Instant occurredAt = Instant.parse(event.path("created_at").asText());
                if (providerId.isBlank() || providerId.length() > 128 || occurredAt.isAfter(clock.instant().plusSeconds(300))) {
                    throw new IllegalArgumentException();
                }
                repository.delivery(id, providerId, state, occurredAt, clock.instant());
            }
            return Map.of("status", "OK");
        } catch (java.io.IOException | IllegalArgumentException | java.time.DateTimeException | GeneralSecurityException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮件回调格式或签名无效");
        }
    }

    private void verify(String id, String timestamp, String signature, byte[] raw) throws GeneralSecurityException {
        if (id == null || id.isBlank() || id.length() > 128 || timestamp == null || signature == null
                || Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp)) > 300) {
            throw new IllegalArgumentException();
        }
        String secret = properties.webhookSecret();
        byte[] key = Base64.getDecoder().decode(secret.startsWith("whsec_") ? secret.substring(6) : secret);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        byte[] expected = mac.doFinal(raw);
        for (String entry : signature.split(" ")) {
            if (entry.startsWith("v1,") && MessageDigest.isEqual(expected, Base64.getDecoder().decode(entry.substring(3)))) {
                return;
            }
        }
        throw new IllegalArgumentException();
    }
}
