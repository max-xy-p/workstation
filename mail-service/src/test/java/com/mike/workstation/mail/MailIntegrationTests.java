package com.mike.workstation.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mail;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "mail.keycloak-token=keycloak-test-token-32-characters-minimum",
        "mail.backend-token=backend-test-token-32-characters-minimum",
        "mail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "mail.resend-api-key=test-key", "mail.worker-enabled=false", "spring.rabbitmq.password=test-password",
        "mail.webhook-secret=whsec_c2VjcmV0", "mail.from=noreply@example.com", "mail.from-name=Workstation"})
@AutoConfigureMockMvc
class MailIntegrationTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String database = System.getProperty("workstation.mail.test.db");
        if (database != null) {
            if (!database.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/workstation_mail_test(?:[?].*)?")) {
                throw new IllegalArgumentException("邮件数据库测试仅允许本机隔离测试库");
            }
            registry.add("spring.datasource.url", () -> database);
            registry.add("spring.datasource.username", () -> "root");
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MAIL_TEST_DB_PASSWORD", ""));
        }
    }
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final String AUTH = "Bearer keycloak-test-token-32-characters-minimum";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MailService service;
    @Autowired MailTaskRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PayloadCipher cipher;
    @Autowired MailProperties properties;
    @Autowired MailOutboxRepository outbox;
    @Autowired MailOutboxRelay relay;
    @MockitoBean MailMessagePublisher publisher;
    @MockitoBean Clock clock;
    @MockitoBean ResendClient resend;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM mail_messages");
        jdbc.update("DELETE FROM mail_outbox");
        jdbc.update("DELETE FROM mail_delivery_events");
        jdbc.update("UPDATE mail_dispatch_clock SET next_at = '2000-01-01 00:00:00'");
        when(clock.instant()).thenReturn(NOW);
    }

    private MailRequest request(String key) {
        return new MailRequest(key, "user@example.com", "验证邮箱", "验证链接 secret-token", "<p>验证链接</p>", NOW.plusSeconds(600));
    }

    private MailWorker worker() {
        return new MailWorker(repository, cipher, json, resend, clock, properties);
    }

    @Test
    void rejectsAnonymousAndMalformedCredentials() throws Exception {
        mvc.perform(post("/internal/mail/messages").contentType("application/json").content(json.writeValueAsString(request("a"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/mail/messages/unknown").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void validatesWithoutEchoingBodyAndCredentials() throws Exception {
        MailRequest bad = new MailRequest("bad", "bad-address", "主题", "secret-token", null, NOW.plusSeconds(10));
        String response = mvc.perform(post("/internal/mail/messages").header("Authorization", AUTH)
                        .contentType("application/json").content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("secret-token"));
        mvc.perform(post("/internal/mail/messages").header("Authorization", AUTH)
                .contentType("application/json").content("{\"text\":\"secret-token\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void persistsEncryptedPayloadAndSeparatesCallers() throws Exception {
        String body = mvc.perform(post("/internal/mail/messages").header("Authorization", AUTH)
                        .contentType("application/json").content(json.writeValueAsString(request("a"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).path("id").asText();
        String payload = jdbc.queryForObject("SELECT payload FROM mail_messages WHERE id = ?", String.class, id);
        assertFalse(payload.contains("secret-token"));
        assertFalse(payload.contains("user@example.com"));
        assertTrue(cipher.decrypt(id, payload).contains("secret-token"));
        mvc.perform(get("/internal/mail/messages/" + id).header("Authorization", "Bearer backend-test-token-32-characters-minimum"))
                .andExpect(status().isNotFound());
        assertNotEquals(id, service.enqueue("backend", request("a")).id());
    }

    @Test
    void duplicateKeyReturnsOriginalAndDifferentContentConflicts() throws Exception {
        MailStatus first = service.enqueue("keycloak", request("a"));
        assertEquals(first.id(), service.enqueue("keycloak", request("a")).id());
        MailRequest changed = new MailRequest("a", "other@example.com", "验证邮箱", "正文", null, NOW.plusSeconds(600));
        mvc.perform(post("/internal/mail/messages").header("Authorization", AUTH)
                .contentType("application/json").content(json.writeValueAsString(changed))).andExpect(status().isConflict());
        assertNotEquals(service.enqueue("keycloak", request("CASE")).id(), service.enqueue("keycloak", request("case")).id());
    }

    @Test
    void concurrentSubmissionsCreateOneTask() throws Exception {
        var pool = Executors.newFixedThreadPool(6);
        try {
            var futures = pool.invokeAll(java.util.stream.IntStream.range(0, 12)
                    .<java.util.concurrent.Callable<String>>mapToObj(i -> () -> service.enqueue("keycloak", request("race")).id()).toList());
            String id = futures.get(0).get(10, TimeUnit.SECONDS);
            for (var future : futures) {
                assertEquals(id, future.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_messages", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsExpiredTooLongAndEmptyMail() {
        for (MailRequest bad : List.of(
                new MailRequest("a", "user@example.com", "主题", "正文", null, NOW),
                new MailRequest("b", "user@example.com", "主题", "正文", null, NOW.plusSeconds(86400)),
                new MailRequest("c", "user@example.com", "主题", "", null, NOW.plusSeconds(60)))) {
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.enqueue("keycloak", bad));
        }
    }

    @Test
    void dispatchSuccessIsSubmittedRatherThanDeliveredAndErasesPayload() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        when(resend.send(eq(task.id()), any())).thenReturn(new ResendClient.Result("provider-1", false, null, 0));
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals("SUBMITTED", service.status("keycloak", task.id()).status());
        assertNull(jdbc.queryForObject("SELECT payload FROM mail_messages WHERE id = ?", String.class, task.id()));
    }

    @Test
    void retryWaitsAndKeepsSameProviderIdempotencyKey() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        when(resend.send(eq(task.id()), any())).thenReturn(new ResendClient.Result(null, true, "PROVIDER_HTTP_429", 90))
                .thenReturn(new ResendClient.Result("provider-1", false, null, 0));
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals("QUEUED", service.status("keycloak", task.id()).status());
        worker().dispatch(new MailDispatch(task.id(), 1));
        verify(resend, times(1)).send(eq(task.id()), any());
        when(clock.instant()).thenReturn(NOW.plusSeconds(91));
        worker().dispatch(new MailDispatch(task.id(), 2));
        verify(resend, times(2)).send(eq(task.id()), any());
        assertEquals("SUBMITTED", service.status("keycloak", task.id()).status());
    }

    @Test
    void permanentErrorsDoNotRetry() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        when(resend.send(anyString(), any())).thenReturn(new ResendClient.Result(null, false, "PROVIDER_HTTP_422", 0));
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals("FAILED", service.status("keycloak", task.id()).status());
        assertNull(repository.byId("keycloak", task.id()).payload());
    }

    @Test
    void expiredMailIsNeverSentAndPayloadIsPurged() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(601));
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals("EXPIRED", service.status("keycloak", task.id()).status());
        assertNull(repository.byId("keycloak", task.id()).payload());
        verifyNoInteractions(resend);
    }

    @Test
    void expiredLeaseCanBeRecoveredAndOldWorkerCannotFinish() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        MailDispatch dispatch = new MailDispatch(task.id(), 1);
        MailTask old = repository.claim(dispatch, NOW).task();
        assertNull(repository.claim(dispatch, NOW.plusSeconds(2)).task());
        MailTask recovered = repository.claim(dispatch, NOW.plusSeconds(61)).task();
        assertEquals(old.id(), recovered.id());
        assertNotEquals(old.leaseToken(), recovered.leaseToken());
        repository.finish(old, "FAILED", null, "OLD_WORKER");
        assertEquals("SENDING", service.status("keycloak", task.id()).status());
        repository.finish(recovered, "SUBMITTED", "provider-1", null);
        assertEquals("SUBMITTED", service.status("keycloak", task.id()).status());
    }

    @Test
    void competingWorkersOnlyClaimOneTaskPerGlobalRateSlot() throws Exception {
        MailStatus first = service.enqueue("keycloak", request("a"));
        MailStatus second = service.enqueue("keycloak", request("b"));
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = pool.invokeAll(java.util.stream.IntStream.range(0, 4)
                    .<java.util.concurrent.Callable<MailTask>>mapToObj(i -> () -> repository.claim(
                            new MailDispatch(i % 2 == 0 ? first.id() : second.id(), 1), NOW).task()).toList());
            int claimed = 0;
            for (var future : futures) {
                if (future.get(10, TimeUnit.SECONDS) != null) {
                    claimed++;
                }
            }
            assertEquals(1, claimed);
            assertNotNull(repository.claim(new MailDispatch(second.id(), 1), NOW.plusSeconds(61)).task());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void validWebhookCanArriveBeforeProviderResponseAndDuplicatesAreSafe() throws Exception {
        MailStatus task = service.enqueue("keycloak", request("a"));
        MailTask claimed = repository.claim(new MailDispatch(task.id(), 1), NOW).task();
        String event = json.writeValueAsString(Map.of("type", "email.delivered", "created_at", NOW.toString(),
                "data", Map.of("email_id", "provider-1")));
        webhook("event-1", NOW.getEpochSecond(), event, true);
        webhook("event-1", NOW.getEpochSecond(), event, true);
        repository.finish(claimed, "SUBMITTED", "provider-1", null);
        assertEquals("DELIVERED", service.status("keycloak", task.id()).status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_delivery_events", Integer.class));
        repository.delivery("older", "provider-1", "DELAYED", NOW.minusSeconds(1), NOW);
        assertEquals("DELIVERED", service.status("keycloak", task.id()).status());
        repository.delivery("bounce", "provider-1", "BOUNCED", NOW.plusSeconds(1), NOW);
        assertEquals("BOUNCED", service.status("keycloak", task.id()).status());
    }

    @Test
    void rejectsForgedExpiredAndTamperedWebhooks() throws Exception {
        webhook("event-1", NOW.getEpochSecond() - 600, "{}", false);
        mvc.perform(post("/hooks/resend").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/hooks/resend").header("svix-id", "event-1")
                .header("svix-timestamp", NOW.getEpochSecond()).header("svix-signature", "v1,AAAA")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mail_delivery_events", Integer.class));
    }

    @Test
    void ciphertextCannotBeMovedToAnotherTaskOrTampered() {
        String encrypted = cipher.encrypt("a", "secret");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("b", encrypted));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("a", encrypted.substring(0, encrypted.length() - 4) + "AAAA"));
        assertFalse(request("a").toString().contains("secret"));
    }

    @Test
    void brokenCiphertextFailsWithoutSendingOrLoggingOriginalContent() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        jdbc.update("UPDATE mail_messages SET payload = 'invalid' WHERE id = ?", task.id());
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals("PAYLOAD_UNREADABLE", service.status("keycloak", task.id()).errorCode());
        assertNull(repository.byId("keycloak", task.id()).payload());
        verifyNoInteractions(resend);
    }

    @Test
    void retriesStopAtAttemptLimitAndRetentionRemovesOldMetadata() {
        MailStatus task = service.enqueue("keycloak", request("a"));
        jdbc.update("UPDATE mail_messages SET attempts = 7 WHERE id = ?", task.id());
        when(resend.send(anyString(), any())).thenReturn(new ResendClient.Result(null, true, "PROVIDER_HTTP_503", 60));
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals("FAILED", service.status("keycloak", task.id()).status());
        assertEquals(8, service.status("keycloak", task.id()).attempts());
        repository.delivery("event-1", "provider-1", "DELIVERED", NOW, NOW);
        repository.purge(NOW.plusSeconds(31L * 86400));
        assertNull(repository.byId("keycloak", task.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mail_delivery_events", Integer.class));
    }

    @Test
    void taskAndOutboxCommitTogetherAndRollbackOnOutboxFailure() {
        MailStatus task = service.enqueue("keycloak", request("atomic"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox WHERE task_id = ?", Integer.class, task.id()));
        jdbc.execute("ALTER TABLE mail_outbox RENAME TO mail_outbox_unavailable");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> service.enqueue("keycloak", request("rollback")));
        } finally {
            jdbc.execute("ALTER TABLE mail_outbox_unavailable RENAME TO mail_outbox");
        }
        assertNull(repository.byKey("keycloak", "rollback"));
    }

    @Test
    void brokerFailureKeepsOutboxAndConfirmedRetryMarksPublished() {
        MailStatus task = service.enqueue("keycloak", request("outbox"));
        doThrow(new IllegalStateException("模拟 Broker 暂不可用")).doNothing().when(publisher).publish(any(), anyLong());
        assertFalse(relay.relayOne());
        assertEquals("PENDING", jdbc.queryForObject("SELECT state FROM mail_outbox WHERE task_id = ?", String.class, task.id()));
        assertFalse(relay.relayOne());
        when(clock.instant()).thenReturn(NOW.plusSeconds(11));
        assertTrue(relay.relayOne());
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT state FROM mail_outbox WHERE task_id = ?", String.class, task.id()));
        verify(publisher, times(2)).publish(eq(new MailDispatch(task.id(), 1)), anyLong());
        verifyNoInteractions(resend);
    }

    @Test
    void unconfirmedOutboxLeaseIsRecoveredAndExpiredTasksAreNotPublished() {
        MailStatus task = service.enqueue("keycloak", request("lease"));
        MailOutboxRepository.Entry abandoned = outbox.claim(NOW);
        assertNotNull(abandoned);
        assertNull(outbox.claim(NOW.plusSeconds(1)));
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        assertTrue(relay.relayOne());
        verify(publisher).publish(eq(new MailDispatch(task.id(), 1)), eq(0L));
        outbox.failed(abandoned, NOW.plusSeconds(32));
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT state FROM mail_outbox WHERE task_id = ?", String.class, task.id()));
        service.enqueue("keycloak", request("expired-outbox"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(601));
        assertTrue(relay.relayOne());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox WHERE state = 'CANCELLED'", Integer.class));
        verify(publisher, times(1)).publish(any(), anyLong());
    }

    @Test
    void retryCommitsNewDispatchVersionAndOldMessagesAreIgnored() {
        MailStatus task = service.enqueue("keycloak", request("version"));
        when(resend.send(anyString(), any())).thenReturn(new ResendClient.Result(null, true, "TEMPORARY", 1));
        worker().dispatch(new MailDispatch(task.id(), 1));
        assertEquals(2, repository.byId("keycloak", task.id()).dispatchVersion());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox WHERE task_id = ?", Integer.class, task.id()));
        assertEquals(0, worker().dispatch(new MailDispatch(task.id(), 1)).delaySeconds());
        assertTrue(worker().dispatch(new MailDispatch(task.id(), 2)).delaySeconds() > 0);
        verify(resend, times(1)).send(anyString(), any());
    }

    private void webhook(String id, long timestamp, String body, boolean accepted) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/hooks/resend").header("svix-id", id).header("svix-timestamp", timestamp)
                .header("svix-signature", "v1," + signature).contentType("application/json").content(body))
                .andExpect(accepted ? status().isOk() : status().isBadRequest());
    }
}
