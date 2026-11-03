package com.mike.workstation.messaging;

import com.mike.workstation.config.InvitationMessagingConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 仅发布 Outbox UUID，不将邮箱、链接或凭据放进 Broker。 */
@Component
public class InvitationPublisher {
    private final RabbitTemplate rabbit;
    public InvitationPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
        rabbit.setMandatory(true);
    }

    public void publish(String id) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(id);
        CorrelationData correlation = new CorrelationData();
        rabbit.send(InvitationMessagingConfig.EXCHANGE, "send",
                new Message(id.getBytes(StandardCharsets.US_ASCII), properties), correlation);
        if (!correlation.getFuture().get(5, TimeUnit.SECONDS).ack()
                || correlation.getReturned() != null) {
            throw new IllegalStateException("邀请消息未被可靠路由");
        }
    }
}
