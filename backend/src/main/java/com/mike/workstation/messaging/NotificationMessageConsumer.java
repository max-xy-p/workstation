package com.mike.workstation.messaging;

import com.mike.workstation.domain.NotificationRefresh;
import com.mike.workstation.service.NotificationStreamHub;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 每个实例消费自己的广播副本，接纳刷新提醒后由容器确认消息。 */
@Component
@ConditionalOnProperty(prefix = "workstation.notifications", name = "enabled", havingValue = "true")
public class NotificationMessageConsumer {
    private final NotificationStreamHub streams;
    private final ObjectMapper json;

    public NotificationMessageConsumer(NotificationStreamHub streams, ObjectMapper json) {
        this.streams = streams;
        this.json = json;
    }

    @RabbitListener(queues = "#{@notificationRefreshQueue.name}", concurrency = "1")
    public void receive(Message message) {
        if (message.getBody().length > 1024) {
            return;
        }
        NotificationRefresh refresh;
        try {
            refresh = json.readValue(message.getBody(), NotificationRefresh.class);
            if (refresh.recipient() == null || refresh.recipient().isEmpty()
                    || refresh.recipient().length() > 255 || refresh.revision() <= 0
                    || refresh.recipient().chars().anyMatch(character -> character < 33 || character > 126)) {
                return;
            }
        } catch (RuntimeException exception) {
            return;
        }
        try {
            streams.changed(refresh.recipient());
        } catch (RuntimeException exception) {
            throw new AmqpRejectAndDontRequeueException("通知刷新提醒暂未处理，稍后重投");
        }
    }
}
