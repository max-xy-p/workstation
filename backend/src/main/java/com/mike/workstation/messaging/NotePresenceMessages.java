package com.mike.workstation.messaging;

import com.mike.workstation.config.NotePresenceMessagingConfig;
import com.mike.workstation.service.NotePresenceStreamHub;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** MQ 只广播笔记主题；丢失提醒由周期快照及下一次心跳恢复。 */
@Component
@ConditionalOnProperty(prefix = "workstation.note-presence", name = "enabled", havingValue = "true")
public class NotePresenceMessages {
    private final RabbitTemplate rabbit;
    private final NotePresenceStreamHub streams;

    public NotePresenceMessages(RabbitTemplate rabbit, NotePresenceStreamHub streams) {
        this.rabbit = rabbit;
        this.streams = streams;
    }

    public void publish(long workspaceId, long noteId) {
        String topic = NotePresenceStreamHub.topic(workspaceId, noteId);
        streams.changed(topic);
        try {
            var properties = new MessageProperties();
            properties.setExpiration("10000");
            rabbit.send(NotePresenceMessagingConfig.EXCHANGE, "",
                    new Message(topic.getBytes(StandardCharsets.US_ASCII), properties));
        } catch (RuntimeException exception) {
            // 在线提示可由 HTTP 快照恢复，MQ 故障不使已完成的续租失败。
        }
    }

    @RabbitListener(queues = "#{@notePresenceQueue.name}", concurrency = "1")
    public void receive(Message message) {
        if (message.getBody().length > 42) {
            return;
        }
        String topic = new String(message.getBody(), StandardCharsets.US_ASCII);
        if (topic.matches("[1-9][0-9]{0,18}:[1-9][0-9]{0,18}")) {
            streams.changed(topic);
        }
    }
}
