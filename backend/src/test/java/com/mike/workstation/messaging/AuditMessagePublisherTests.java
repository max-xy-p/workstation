package com.mike.workstation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.mike.workstation.config.AuditMessagingConfig;
import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.domain.PrincipalType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class AuditMessagePublisherTests {
    @Mock RabbitTemplate rabbitTemplate;
    private AuditMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        var properties =
                new AuditOutboxProperties(
                        10,
                        Duration.ofMinutes(2),
                        Duration.ofSeconds(5),
                        3,
                        Duration.ofSeconds(10));
        publisher = new AuditMessagePublisher(rabbitTemplate, new ObjectMapper(), properties);
    }

    @Test
    void brokerAckPublishesPersistentMessageWithCorrelatedEventId() throws Exception {
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbitTemplate)
                .send(
                        eq(AuditMessagingConfig.EXCHANGE),
                        eq(AuditMessagingConfig.ROUTING_KEY),
                        any(Message.class),
                        any(CorrelationData.class));

        publisher.publish(event());

        var messageCaptor = ArgumentCaptor.forClass(Message.class);
        var correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate)
                .send(
                        eq(AuditMessagingConfig.EXCHANGE),
                        eq(AuditMessagingConfig.ROUTING_KEY),
                        messageCaptor.capture(),
                        correlationCaptor.capture());
        assertThat(correlationCaptor.getValue().getId()).isEqualTo("event-1");
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo("event-1");
        assertThat(messageCaptor.getValue().getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8))
                .contains("event-1", "PROJECT_CREATE", "actor-1");
    }

    @Test
    void brokerNackCausesRelayRetry() {
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(
                                            new CorrelationData.Confirm(false, "broker rejected"));
                            return null;
                        })
                .when(rabbitTemplate)
                .send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broker rejected");
    }

    @Test
    void unroutableMessageIsNotTreatedAsDelivered() {
        doAnswer(
                        invocation -> {
                            Message message = invocation.getArgument(2);
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation.setReturned(
                                    new ReturnedMessage(
                                            message,
                                            312,
                                            "NO_ROUTE",
                                            AuditMessagingConfig.EXCHANGE,
                                            AuditMessagingConfig.ROUTING_KEY));
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbitTemplate)
                .send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO_ROUTE");
    }

    @Test
    void auditSnapshotPreservesFieldsForFinalEvent() {
        var finalEvent = AuditMessage.from(event()).toEvent();

        assertThat(finalEvent.getEventId()).isEqualTo("event-1");
        assertThat(finalEvent.getWorkspaceId()).isEqualTo(9L);
        assertThat(finalEvent.getActorSubject()).isEqualTo("actor-1");
        assertThat(finalEvent.getAction()).isEqualTo("PROJECT_CREATE");
        assertThat(finalEvent.getResourceId()).isEqualTo(7L);
        assertThat(finalEvent.getTraceId()).isEqualTo("trace-1");
    }

    private AuditOutbox event() {
        AuditOutbox event = new AuditOutbox();
        event.setEventId("event-1");
        event.setWorkspaceId(9L);
        event.setActorSubject("actor-1");
        event.setActorType(PrincipalType.USER);
        event.setAction("PROJECT_CREATE");
        event.setResourceType("PROJECT");
        event.setResourceId(7L);
        event.setResult(AuditResult.SUCCESS);
        event.setOccurredAt(LocalDateTime.of(2026, 9, 25, 12, 0));
        event.setTraceId("trace-1");
        return event;
    }
}
