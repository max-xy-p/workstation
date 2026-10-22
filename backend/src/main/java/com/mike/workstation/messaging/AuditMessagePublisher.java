package com.mike.workstation.messaging;

import com.mike.workstation.config.AuditMessagingConfig;
import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/** 使用持久化消息、mandatory 路由检查和 Publisher Confirm 发布审计事件。 */
@Component
public class AuditMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AuditOutboxProperties properties;

    public AuditMessagePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AuditOutboxProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        // mandatory=true 可发现“Exchange 存在但没有匹配队列”的静默丢消息问题。
        this.rabbitTemplate.setMandatory(true);
    }

    /**
     * 发布一条事件并同步等待 Broker 确认。
     *
     * <p>这里的“成功”仅表示 RabbitMQ 已接管持久化消息，不表示消费者已经写入 audit_events。 超时、Nack 或无法路由都会抛出异常，由 Relay 安排下一次重试。
     */
    public void publish(AuditOutbox event) throws Exception {
        AuditMessage payload = AuditMessage.from(event);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setMessageId(event.getEventId());
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setHeader("auditEventId", event.getEventId());
        Message message = new Message(objectMapper.writeValueAsBytes(payload), messageProperties);
        // CorrelationData 将异步 Confirm/Return 精确关联回当前 eventId。
        CorrelationData correlation = new CorrelationData(event.getEventId());

        rabbitTemplate.send(
                AuditMessagingConfig.EXCHANGE,
                AuditMessagingConfig.ROUTING_KEY,
                message,
                correlation);

        // 有限等待，避免 RabbitMQ 异常时 XXL-JOB 执行线程永久阻塞。
        CorrelationData.Confirm confirm =
                correlation
                        .getFuture()
                        .get(properties.publishConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.ack()) {
            throw new IllegalStateException("RabbitMQ 拒绝审计消息：" + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException(
                    "RabbitMQ 未找到审计消息路由：" + correlation.getReturned().getReplyText());
        }
    }
}
