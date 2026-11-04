package com.mike.workstation.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.NotificationMessagingConfig;
import com.mike.workstation.domain.NotificationRefresh;
import com.mike.workstation.service.NotificationStreamHub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;

/** 每个实例队列获得独立副本，离线期间保留，失败只在该实例重投。 */
@EnabledIfSystemProperty(named = "workstation.notification.test.rabbit", matches = "true")
class NotificationBrokerTests {
    @Test
    void durableFanoutQueuesRetainOfflineCopiesAndRetryOnlyTheirOwnConsumer() throws Exception {
        var factory = new CachingConnectionFactory("127.0.0.1",
                Integer.getInteger("workstation.notification.test.rabbit.port", 5672));
        factory.setUsername("invite-test");
        factory.setPassword(System.getenv().getOrDefault("INVITATION_TEST_RABBIT_PASSWORD", "invite-isolated-test-password"));
        factory.setPublisherConfirmType(ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        var admin = new RabbitAdmin(factory);
        var first = new SimpleMessageListenerContainer(factory);
        var second = new SimpleMessageListenerContainer(factory);
        String suffix = UUID.randomUUID().toString();
        String a = "test-a-" + suffix;
        String b = "test-b-" + suffix;
        Queue qa = NotificationMessagingConfig.mainQueue(a);
        Queue qb = NotificationMessagingConfig.mainQueue(b);
        var ta = NotificationMessagingConfig.topology(a, qa);
        var tb = NotificationMessagingConfig.topology(b, qb);
        try {
            for (var topology : List.of(ta, tb)) {
                for (Exchange exchange : topology.getDeclarablesByType(Exchange.class)) admin.declareExchange(exchange);
                for (Queue queue : topology.getDeclarablesByType(Queue.class)) admin.declareQueue(queue);
            }
            admin.declareQueue(qa);
            admin.declareQueue(qb);
            for (var topology : List.of(ta, tb)) {
                for (Binding binding : topology.getDeclarablesByType(Binding.class)) admin.declareBinding(binding);
            }
            var rabbit = new RabbitTemplate(factory);
            var json = new ObjectMapper();
            var publisher = new NotificationMessagePublisher(rabbit, json);
            publisher.publish(new NotificationRefresh("recipient", 1));
            var persisted = rabbit.receive(qa.getName(), 5000);
            assertThat(persisted).isNotNull();
            assertThat(persisted.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(json.readValue(persisted.getBody(), NotificationRefresh.class).revision()).isEqualTo(1);
            assertThat(admin.getQueueProperties(qb.getName()).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(1L);
            var local = mock(NotificationStreamHub.class);
            var remote = mock(NotificationStreamHub.class);
            var ca = new NotificationMessageConsumer(local, json);
            var cb = new NotificationMessageConsumer(remote, json);
            doThrow(new IllegalStateException("模拟实例暂时不可处理")).doNothing().when(remote).changed("recipient");
            first.setQueueNames(qa.getName());
            first.setDefaultRequeueRejected(false);
            first.setMessageListener((MessageListener) ca::receive);
            first.start();
            publisher.publish(new NotificationRefresh("recipient", 2));
            verify(local, timeout(5000)).changed("recipient");
            assertThat(admin.getQueueProperties(qb.getName()).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(2L);
            second.setQueueNames(qb.getName());
            second.setDefaultRequeueRejected(false);
            second.setMessageListener((MessageListener) cb::receive);
            second.start();
            // 第二个版本可先被处理，重投只表示刷新，HTTP 始终查询最新状态。
            verify(remote, timeout(25000).times(3)).changed("recipient");
            verify(local, times(1)).changed("recipient");
            first.stop();
            second.stop();
            for (var topology : List.of(ta, tb)) {
                for (Binding binding : topology.getDeclarablesByType(Binding.class)) {
                    if (NotificationMessagingConfig.EXCHANGE.equals(binding.getExchange())) admin.removeBinding(binding);
                }
            }
            assertThatThrownBy(() -> publisher.publish(new NotificationRefresh("recipient", 3))).isInstanceOf(IllegalStateException.class);
        } finally {
            first.stop();
            second.stop();
            for (String instance : List.of(a, b)) {
                admin.deleteQueue(NotificationMessagingConfig.queueName(instance));
                admin.deleteQueue(NotificationMessagingConfig.queueName(instance) + ".retry");
            }
            admin.deleteExchange(NotificationMessagingConfig.EXCHANGE);
            admin.deleteExchange(NotificationMessagingConfig.RETRY_EXCHANGE);
            factory.destroy();
        }
    }
}
