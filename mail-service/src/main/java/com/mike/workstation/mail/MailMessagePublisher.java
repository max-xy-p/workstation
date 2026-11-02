package com.mike.workstation.mail;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MailMessagePublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public MailMessagePublisher(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
        rabbit.setMandatory(true);
    }

    public void publish(MailDispatch dispatch, long delaySeconds) {
        send(json.writeValueAsBytes(dispatch), dispatch.taskId() + ":" + dispatch.version(),
                MailMessagingConfig.route(delaySeconds));
    }

    public void dead(MailDispatch dispatch) {
        send(json.writeValueAsBytes(dispatch), dispatch.taskId() + ":" + dispatch.version(), MailMessagingConfig.DEAD);
    }

    public void invalid() {
        // 无效消息不复制原文，避免将意外携带的正文或凭据写入死信队列。
        send("无效邮件投递引用".getBytes(StandardCharsets.UTF_8), "invalid", MailMessagingConfig.DEAD);
    }

    private void send(byte[] body, String id, String route) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(id);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        CorrelationData correlation = new CorrelationData();
        try {
            rabbit.send(MailMessagingConfig.EXCHANGE, route, new Message(body, properties), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ 未确认邮件消息或无法路由");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("邮件消息发布被中断");
        } catch (Exception error) {
            throw new IllegalStateException("邮件消息发布未确认");
        }
    }
}
