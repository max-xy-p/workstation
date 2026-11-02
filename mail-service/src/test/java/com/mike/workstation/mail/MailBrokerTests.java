package com.mike.workstation.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@EnabledIfSystemProperty(named = "workstation.mail.test.rabbit", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mail-broker;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "mail.keycloak-token=keycloak-test-token-32-characters-minimum",
        "mail.backend-token=backend-test-token-32-characters-minimum",
        "mail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "mail.resend-api-key=test-key", "mail.worker-enabled=false",
        "mail.from=noreply@example.com", "mail.from-name=Workstation"})
class MailBrokerTests {
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        MailIntegrationTests.database(registry);
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> System.getProperty("workstation.mail.test.rabbit.port", "5672"));
        registry.add("spring.rabbitmq.username", () -> "mail-test");
        registry.add("spring.rabbitmq.password", () -> System.getenv().getOrDefault("MAIL_TEST_RABBIT_PASSWORD", "mail-ci-isolated-password"));
    }

    @Autowired MailService service;
    @Autowired MailOutboxRelay relay;
    @Autowired MailMessagePublisher publisher;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ResendClient resend;
    @MockitoSpyBean MailWorker worker;

    @BeforeEach
    void prepare() {
        listeners.getListenerContainers().forEach(container -> container.stop());
        admin.initialize();
        for (long seconds : MailMessagingConfig.DELAYS) {
            admin.purgeQueue("workstation.mail.retry." + seconds + ".queue");
        }
        admin.purgeQueue(MailMessagingConfig.QUEUE);
        admin.purgeQueue(MailMessagingConfig.DEAD_QUEUE);
        jdbc.update("DELETE FROM mail_outbox");
        jdbc.update("DELETE FROM mail_messages");
        jdbc.update("DELETE FROM mail_delivery_events");
        jdbc.update("UPDATE mail_dispatch_clock SET next_at = '2000-01-01 00:00:00'");
    }

    @AfterEach
    void stop() {
        listeners.getListenerContainers().forEach(container -> container.stop());
    }

    private MailStatus enqueue() {
        return service.enqueue("keycloak", new MailRequest(UUID.randomUUID().toString(), "user@example.com", "验证码",
                "仅模拟发送的正文", null, Instant.now().plusSeconds(300)));
    }

    private void start() {
        listeners.getListenerContainers().forEach(container -> container.start());
    }

    @Test
    void realConsumerAcknowledgesAfterDatabaseCommitAndAbsorbsDuplicates() throws Exception {
        MailStatus task = enqueue();
        when(resend.send(eq(task.id()), any())).thenReturn(new ResendClient.Result("provider-1", false, null, 0));
        assertTrue(relay.relayOne());
        publisher.publish(new MailDispatch(task.id(), 1), 0);
        start();
        await(() -> "SUBMITTED".equals(service.status("keycloak", task.id()).status()), 10);
        await(() -> admin.getQueueInfo(MailMessagingConfig.QUEUE).getMessageCount() == 0, 5);
        verify(resend, times(1)).send(eq(task.id()), any());
    }

    @Test
    void temporaryProviderFailureUsesBrokerDelayAndNewDispatchVersion() throws Exception {
        MailStatus task = enqueue();
        when(resend.send(eq(task.id()), any())).thenReturn(new ResendClient.Result(null, true, "TEMPORARY", 1))
                .thenReturn(new ResendClient.Result("provider-2", false, null, 0));
        assertTrue(relay.relayOne());
        start();
        await(() -> service.status("keycloak", task.id()).attempts() == 1
                && "QUEUED".equals(service.status("keycloak", task.id()).status()), 5);
        assertTrue(relay.relayOne());
        assertNotNull(admin.getQueueInfo("workstation.mail.retry.10.queue"));
        await(() -> "SUBMITTED".equals(service.status("keycloak", task.id()).status()), 20);
        assertEquals(2, service.status("keycloak", task.id()).attempts());
        verify(resend, times(2)).send(eq(task.id()), any());
    }

    @Test
    void consumerFailureIsDeadLetteredAndRecoveredWithoutImmediateRequeue() throws Exception {
        MailStatus task = enqueue();
        doThrow(new IllegalStateException("模拟临时数据库故障")).doCallRealMethod().when(worker).dispatch(any());
        when(resend.send(eq(task.id()), any())).thenReturn(new ResendClient.Result("provider-3", false, null, 0));
        relay.relayOne();
        start();
        await(() -> admin.getQueueInfo("workstation.mail.retry.10.queue").getMessageCount() > 0, 5);
        verifyNoInteractions(resend);
        await(() -> "SUBMITTED".equals(service.status("keycloak", task.id()).status()), 20);
    }

    @Test
    void malformedAndPermanentlyFailedMessagesGoToDeadQueue() throws Exception {
        MailStatus task = enqueue();
        when(resend.send(eq(task.id()), any())).thenReturn(new ResendClient.Result(null, false, "REJECTED", 0));
        relay.relayOne();
        rabbit.send(MailMessagingConfig.EXCHANGE, MailMessagingConfig.SEND,
                new Message("sensitive-invalid-content".getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties()));
        start();
        await(() -> admin.getQueueInfo(MailMessagingConfig.DEAD_QUEUE).getMessageCount() == 2, 10);
        assertEquals("FAILED", service.status("keycloak", task.id()).status());
        Message first = rabbit.receive(MailMessagingConfig.DEAD_QUEUE, 1000);
        Message second = rabbit.receive(MailMessagingConfig.DEAD_QUEUE, 1000);
        assertFalse(new String(first.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("sensitive"));
        assertFalse(new String(second.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("sensitive"));
    }

    @Test
    void unroutableMessageDoesNotMarkOutboxPublished() {
        MailStatus task = enqueue();
        Binding binding = new Binding(MailMessagingConfig.QUEUE, Binding.DestinationType.QUEUE,
                MailMessagingConfig.EXCHANGE, MailMessagingConfig.SEND, null);
        admin.removeBinding(binding);
        try {
            assertFalse(relay.relayOne());
            assertEquals("PENDING", jdbc.queryForObject("SELECT state FROM mail_outbox WHERE task_id = ?", String.class, task.id()));
        } finally {
            admin.declareBinding(binding);
        }
    }

    private void await(BooleanSupplier condition, long seconds) throws Exception {
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("隔离 RabbitMQ 邮件处理未在期限内完成");
    }
}
