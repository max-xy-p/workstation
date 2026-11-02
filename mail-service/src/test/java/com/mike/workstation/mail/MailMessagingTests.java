package com.mike.workstation.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class MailMessagingTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private final MailDispatch dispatch = new MailDispatch(UUID.randomUUID().toString(), 1);

    @Test
    void publisherRequiresPositiveConfirmAndNoReturn() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MailMessagePublisher publisher = new MailMessagePublisher(rabbit, json);
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals(dispatch, json.readValue(message.getBody(), MailDispatch.class));
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publish(dispatch, 10);
        verify(rabbit).setMandatory(true);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "模拟拒绝"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThrows(IllegalStateException.class, () -> publisher.publish(dispatch, 0));
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "exchange", "missing"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThrows(IllegalStateException.class, () -> publisher.publish(dispatch, 0));
    }

    @Test
    void consumerOnlyCompletesAfterDurableDelayOrFailurePublication() {
        MailWorker worker = mock(MailWorker.class);
        MailMessagePublisher publisher = mock(MailMessagePublisher.class);
        MailMessageConsumer consumer = new MailMessageConsumer(worker, publisher, json);
        Message message = new Message(json.writeValueAsBytes(dispatch), new MessageProperties());
        when(worker.dispatch(dispatch)).thenReturn(new MailDeliveryResult(30, false));
        consumer.consume(message);
        verify(publisher).publish(dispatch, 30);
        doThrow(new IllegalStateException("模拟 Confirm 失败")).when(publisher).publish(dispatch, 30);
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(message));
        when(worker.dispatch(dispatch)).thenReturn(new MailDeliveryResult(0, true));
        consumer.consume(message);
        verify(publisher).dead(dispatch);
        when(worker.dispatch(dispatch)).thenThrow(new IllegalStateException("模拟数据库断开"));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(message));
    }

    @Test
    void invalidPayloadIsQuarantinedWithoutPassingItsContent() {
        MailWorker worker = mock(MailWorker.class);
        MailMessagePublisher publisher = mock(MailMessagePublisher.class);
        MailMessageConsumer consumer = new MailMessageConsumer(worker, publisher, json);
        consumer.consume(new Message("敏感正文不是任务引用".getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties()));
        consumer.consume(new Message(new byte[513], new MessageProperties()));
        verify(publisher, times(2)).invalid();
        verifyNoInteractions(worker);
        assertThrows(IllegalArgumentException.class, () -> new MailDispatch("invalid", 1));
        assertThrows(IllegalArgumentException.class, () -> new MailDispatch(dispatch.taskId(), 0));
    }

    @Test
    void delayBucketsDoNotReleaseBeforeRequestedDelay() {
        assertEquals(MailMessagingConfig.SEND, MailMessagingConfig.route(0));
        assertEquals("mail.retry.1", MailMessagingConfig.route(1));
        assertEquals("mail.retry.60", MailMessagingConfig.route(11));
        assertEquals("mail.retry.3600", MailMessagingConfig.route(7200));
        var topology = new MailMessagingConfig().mailTopology();
        var queues = topology.getDeclarablesByType(org.springframework.amqp.core.Queue.class);
        assertEquals(8, queues.size());
        for (var queue : queues) {
            assertTrue(queue.isDurable());
            assertEquals("quorum", queue.getArguments().get("x-queue-type"));
            if (!queue.getName().equals(MailMessagingConfig.DEAD_QUEUE)) {
                assertEquals("at-least-once", queue.getArguments().get("x-dead-letter-strategy"));
                assertEquals("reject-publish", queue.getArguments().get("x-overflow"));
            }
        }
    }
}
