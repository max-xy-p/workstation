package com.mike.workstation.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.InvitationMessagingConfig;
import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.domain.WorkspaceInvitation;
import com.mike.workstation.mapper.InvitationMapper;
import com.mike.workstation.service.InvitationDeliveryService;
import com.mike.workstation.service.InvitationMailClient;
import com.mike.workstation.service.InvitationTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/** 仅连接显式启用的隔离 Broker，真实验证确认、延迟恢复及重复消息吸收。 */
@EnabledIfSystemProperty(named = "workstation.invitation.test.rabbit", matches = "true")
class InvitationBrokerTests {
    @Test
    void confirmedReferencesRecoverViaDelayAndDuplicateCannotCreateSecondMail() throws Exception {
        var factory = new CachingConnectionFactory("127.0.0.1",
                Integer.getInteger("workstation.invitation.test.rabbit.port", 5672));
        factory.setUsername("invite-test");
        factory.setPassword(System.getenv().getOrDefault("INVITATION_TEST_RABBIT_PASSWORD", "invite-isolated-test-password"));
        factory.setPublisherConfirmType(ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        var admin = new RabbitAdmin(factory);
        var listener = new SimpleMessageListenerContainer(factory);
        try {
            var topology = new InvitationMessagingConfig().invitationTopology();
            for (Exchange exchange : topology.getDeclarablesByType(Exchange.class)) admin.declareExchange(exchange);
            for (Queue queue : topology.getDeclarablesByType(Queue.class)) {
                admin.declareQueue(queue);
                admin.purgeQueue(queue.getName(), false);
            }
            for (Binding binding : topology.getDeclarablesByType(Binding.class)) admin.declareBinding(binding);
            var rabbit = new RabbitTemplate(factory);
            var publisher = new InvitationPublisher(rabbit);
            String id = UUID.randomUUID().toString();
            publisher.publish(id);
            Message stored = rabbit.receive(InvitationMessagingConfig.QUEUE, 5000);
            assertThat(stored).isNotNull();
            assertThat(stored.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(new String(stored.getBody(), java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo(id);

            var props = new InvitationProperties(true, "https://work.example",
                    Base64.getEncoder().encodeToString(new byte[32]), "http://mail", "x".repeat(32));
            var tokens = new InvitationTokenService(props);
            var invitation = new WorkspaceInvitation();
            invitation.setId(UUID.randomUUID().toString());
            invitation.setEmail("guest@example.com");
            invitation.setWorkspaceName("研发");
            invitation.setMailStatus("PENDING");
            invitation.setStatus("PENDING");
            invitation.setExpiresAt(LocalDateTime.now(ZoneId.systemDefault()).plusHours(1));
            invitation.setTokenHash(tokens.hash(tokens.token(invitation.getId())));
            var mapper = mock(InvitationMapper.class);
            when(mapper.findDelivery(id)).thenReturn(invitation);
            when(mapper.completed(eq(id), anyString())).thenAnswer(call -> {
                invitation.setMailStatus(call.getArgument(1));
                return 1;
            });
            var mail = mock(InvitationMailClient.class);
            var attempts = new AtomicInteger();
            doAnswer(call -> {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("模拟接收失败");
                return null;
            }).when(mail).enqueue(any());
            var consumer = new InvitationConsumer(new InvitationDeliveryService(mapper, tokens, mail, props));
            listener.setQueueNames(InvitationMessagingConfig.QUEUE);
            listener.setDefaultRequeueRejected(false);
            listener.setMessageListener((MessageListener) consumer::receive);
            listener.start();
            publisher.publish(id);
            await().atMost(25, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(mapper).completed(id, "ACCEPTED"));
            assertThat(attempts.get()).isEqualTo(2);
            publisher.publish(id);
            await().during(500, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(attempts.get()).isEqualTo(2));
            verify(mail, times(2)).enqueue(any());
            verify(mapper).completed(id, "ACCEPTED");

            // 删除路由后必须报错，不能把无法路由误当作已发布。
            listener.stop();
            for (Binding binding : topology.getDeclarablesByType(Binding.class)) {
                if ("send".equals(binding.getRoutingKey())) admin.removeBinding(binding);
            }
            assertThatThrownBy(() -> publisher.publish(UUID.randomUUID().toString()))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            listener.stop();
            admin.deleteQueue(InvitationMessagingConfig.QUEUE);
            admin.deleteQueue("workstation.invitation.retry.queue");
            admin.deleteExchange(InvitationMessagingConfig.EXCHANGE);
            factory.destroy();
        }
    }
}
