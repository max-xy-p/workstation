package com.mike.workstation.keycloak.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.keycloak.email.EmailException;
import org.keycloak.util.JsonSerialization;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class WorkstationEmailSenderTests {
    @Test
    void sendsOriginalKeycloakContentAndOnlyAcceptsDurableAcceptance() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Integer> status = new AtomicReference<>(202);
        server.createContext(
                "/internal/mail/messages",
                exchange -> {
                    assertEquals(
                            "Bearer " + "a".repeat(32),
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    received.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(status.get(), -1);
                    exchange.close();
                });
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            Instant now = Instant.parse("2026-10-29T00:00:00Z");
            var sender =
                    new WorkstationEmailSender(
                            client,
                            URI.create(
                                    "http://127.0.0.1:"
                                            + server.getAddress().getPort()
                                            + "/internal/mail/messages"),
                            "a".repeat(32),
                            Clock.fixed(now, ZoneOffset.UTC),
                            300);
            sender.send(Map.of(), "user@example.com", "验证邮箱", "原始链接", "<a>原始链接</a>");
            Map<?, ?> body = JsonSerialization.readValue(received.get(), Map.class);
            assertEquals("user@example.com", body.get("to"));
            assertEquals("原始链接", body.get("text"));
            assertEquals("<a>原始链接</a>", body.get("html"));
            assertEquals(now.plusSeconds(300).toString(), body.get("expiresAt"));
            assertNotNull(body.get("requestKey"));
            sender.send(
                    Map.of(WorkstationEmailSender.CODE_EXPIRES_AT, now.plusSeconds(60).toString()),
                    "user@example.com",
                    "验证码",
                    "验证码正文",
                    null);
            Map<?, ?> limitedBody = JsonSerialization.readValue(received.get(), Map.class);
            assertEquals(now.plusSeconds(60).toString(), limitedBody.get("expiresAt"));
            status.set(401);
            assertThrows(
                    EmailException.class,
                    () -> sender.send(Map.of(), "user@example.com", "验证", "链接", null));
            sender.validate(Map.of());
            sender.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void endpointCannotLeakCredentialsThroughRedirectOrRemotePlaintext() {
        assertEquals(
                "http://mail-service:8082/internal/mail/messages",
                WorkstationEmailSenderFactory.endpoint("http://mail-service:8082").toString());
        assertEquals(
                "https://mail.example.com/internal/mail/messages",
                WorkstationEmailSenderFactory.endpoint("https://mail.example.com/").toString());
        for (String bad :
                new String[] {
                    "http://mail.example.com",
                    "https://user:secret@mail.example.com",
                    "https://mail.example.com?token=secret",
                    "https://mail.example.com/path",
                    "file:///tmp/mail"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WorkstationEmailSenderFactory.endpoint(bad));
        }
    }

    @Test
    void factoryIsDiscoverableAndCredentialValidationFailsClosed() {
        assertTrue(
                java.util.ServiceLoader.load(org.keycloak.email.EmailSenderProviderFactory.class)
                        .stream()
                        .anyMatch(
                                provider ->
                                        provider.type() == WorkstationEmailSenderFactory.class));
        try (HttpClient client = HttpClient.newHttpClient()) {
            var sender =
                    new WorkstationEmailSender(
                            client, URI.create("http://localhost:8082"), "", Clock.systemUTC(), 60);
            assertThrows(EmailException.class, () -> sender.validate(Map.of()));
        }
    }

    @Test
    void factoryCapsQueueLifetimeByRealmLinkExpiryAndClosesSharedClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext(
                "/internal/mail/messages",
                exchange -> {
                    received.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(202, -1);
                    exchange.close();
                });
        server.start();
        var factory = new WorkstationEmailSenderFactory();
        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> factory.configure("http://localhost:8082", null));
            factory.configure("http://localhost:" + server.getAddress().getPort(), "a".repeat(32));
            var session = mock(org.keycloak.models.KeycloakSession.class);
            var context = mock(org.keycloak.models.KeycloakContext.class);
            var realm = mock(org.keycloak.models.RealmModel.class);
            when(session.getContext()).thenReturn(context);
            when(context.getRealm()).thenReturn(realm);
            when(realm.getActionTokenGeneratedByUserLifespan()).thenReturn(30);
            Instant before = Instant.now();
            factory.create(session).send(Map.of(), "user@example.com", "验证", "链接", null);
            Map<?, ?> body = JsonSerialization.readValue(received.get(), Map.class);
            Instant expires = Instant.parse(body.get("expiresAt").toString());
            assertTrue(expires.isAfter(before.plusSeconds(29)));
            assertTrue(expires.isBefore(Instant.now().plusSeconds(31)));
            when(context.getRealm()).thenReturn(null);
            factory.create(session).validate(Map.of());
            factory.postInit(null);
            assertEquals("workstation-mail", factory.getId());
        } finally {
            factory.close();
            server.stop(0);
        }
    }
}
