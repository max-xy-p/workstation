package com.mike.workstation.mail;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResendClientTests {
    private HttpServer server;
    private int code;
    private String response;
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add("Retry-After", "120");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ResendClient client() {
        var properties = new MailProperties("a".repeat(32), "b".repeat(32), "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "test-key", "noreply@example.com", "Workstation", "support@example.com", false, "");
        return new ResendClient(properties, JsonMapper.builder().build(), HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"));
    }

    private ResendClient.Result send() {
        return client().send("task-1", new MailRequest("key", "user@example.com", "验证", "正文", null, Instant.now().plusSeconds(60)));
    }

    @Test
    void sendsConfiguredSenderAndStableIdempotencyKey() {
        code = 200;
        response = "{\"id\":\"provider-1\"}";
        assertEquals("provider-1", send().providerId());
        assertEquals("workstation-mail/task-1", key.get());
        assertTrue(body.get().contains("noreply@example.com"));
        assertTrue(body.get().contains("reply_to"));
    }

    @Test
    void handlesQuotaAndTemporaryErrorsWithRetryAfter() {
        for (int value : new int[]{429, 500, 503}) {
            code = value;
            response = "{\"message\":\"secret-provider-error\"}";
            var result = send();
            assertTrue(result.retryable());
            assertEquals(120, result.retryAfterSeconds());
            assertFalse(result.errorCode().contains("secret"));
        }
    }

    @Test
    void permanentErrorsAndIdempotencyMismatchAreNotRetried() {
        for (int value : new int[]{401, 403, 409, 422}) {
            code = value;
            response = "{\"name\":\"invalid_idempotent_request\"}";
            assertFalse(send().retryable());
        }
    }

    @Test
    void concurrentIdempotencyAndMalformedSuccessAreRetried() {
        code = 409;
        response = "{\"name\":\"concurrent_idempotent_requests\"}";
        assertTrue(send().retryable());
        code = 200;
        response = "{}";
        assertTrue(send().retryable());
        response = "invalid-json";
        assertTrue(send().retryable());
    }
}
