package com.mike.workstation.messaging;

import com.mike.workstation.config.NotificationMessagingConfig;
import com.mike.workstation.domain.NotificationRefresh;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 广播已提交收件箱版本，Confirm 和 Return 成功后才清除待发布标记。 */
@Component
public class NotificationMessagePublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public NotificationMessagePublisher(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
        rabbit.setMandatory(true);
    }

    public void publish(NotificationRefresh refresh) throws Exception {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(UUID.randomUUID().toString());
        var correlation = new CorrelationData();
        rabbit.send(NotificationMessagingConfig.EXCHANGE, "",
                new Message(json.writeValueAsBytes(refresh), properties), correlation);
        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new IllegalStateException("通知刷新提醒未被可靠路由");
        }
    }
}
