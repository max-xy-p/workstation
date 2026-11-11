package com.mike.workstation.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import com.mike.workstation.config.NotePresenceMessagingConfig;
import com.mike.workstation.service.NotePresenceStreamHub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 临时广播队列只服务当前连接，断线删除而不积压旧在线提醒。 */
@EnabledIfSystemProperty(named = "workstation.notification.test.rabbit", matches = "true")
class NotePresenceBrokerTests {
    @Test
    void eachInstanceReceivesItsOwnTopicAndTemporaryQueuesDisappearOnDisconnect() throws Exception {
        var a = factory();
        var b = factory();
        var adminA = new RabbitAdmin(a);
        var adminB = new RabbitAdmin(b);
        var config = new NotePresenceMessagingConfig();
        var qa = config.notePresenceQueue();
        var qb = config.notePresenceQueue();
        var first = new SimpleMessageListenerContainer(a);
        var second = new SimpleMessageListenerContainer(b);
        var local = mock(NotePresenceStreamHub.class);
        var remote = mock(NotePresenceStreamHub.class);
        var sender = new NotePresenceMessages(new RabbitTemplate(a), local);
        var receiver = new NotePresenceMessages(new RabbitTemplate(b), remote);
        try {
            for (var admin : new RabbitAdmin[] {adminA, adminB}) {
                admin.declareExchange(config.notePresenceExchange());
            }
            adminA.declareQueue(qa);
            adminB.declareQueue(qb);
            adminA.declareBinding(config.notePresenceBinding(qa, config.notePresenceExchange()));
            adminB.declareBinding(config.notePresenceBinding(qb, config.notePresenceExchange()));
            first.setQueueNames(qa.getName());
            first.setMessageListener((MessageListener) sender::receive);
            second.setQueueNames(qb.getName());
            second.setMessageListener((MessageListener) receiver::receive);
            first.start();
            second.start();
            sender.publish(1, 2);
            verify(remote, timeout(5000)).changed("1:2");
            verify(local, timeout(5000).times(2)).changed("1:2");
            receiver.receive(new Message("invalid".getBytes(StandardCharsets.US_ASCII)));
            receiver.receive(new Message(new byte[43]));
            verifyNoMoreInteractions(remote);
            assertThat(qa.isExclusive()).isTrue();
            assertThat(qa.isAutoDelete()).isTrue();
            assertThat(qa.isDurable()).isFalse();
            assertThat(qa.getArguments()).containsEntry("x-message-ttl", 10000);
            second.stop();
            b.destroy();
            // 被动查询确认队列不存在；不能从另一个连接读取仍存活的 exclusive 队列。
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> adminA.getQueueProperties(qb.getName()) == null);
        } finally {
            first.stop();
            second.stop();
            a.destroy();
            b.destroy();
        }
    }

    private CachingConnectionFactory factory() {
        var factory = new CachingConnectionFactory("127.0.0.1",
                Integer.getInteger("workstation.notification.test.rabbit.port", 5672));
        factory.setUsername("invite-test");
        factory.setPassword(System.getenv().getOrDefault("INVITATION_TEST_RABBIT_PASSWORD", "invite-isolated-test-password"));
        return factory;
    }
}
