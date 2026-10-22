package com.mike.workstation.messaging;

import com.mike.workstation.config.AuditMessagingConfig;
import com.mike.workstation.mapper.AuditMapper;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/** 消费审计消息并以 eventId 幂等写入最终审计表。 */
@Component
public class AuditMessageConsumer {
    private final AuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public AuditMessageConsumer(AuditMapper auditMapper, ObjectMapper objectMapper) {
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 将消息快照写入最终审计表。
     *
     * <p>数据库提交成功后监听容器才会 ACK。若提交后、ACK 前进程中断，RabbitMQ 会再次投递； audit_events.event_id
     * 的唯一约束会吸收重复事件。持续失败的消息在有限重试后进入死信队列。
     */
    @RabbitListener(queues = AuditMessagingConfig.QUEUE)
    @Transactional
    public void consume(Message message) {
        AuditMessage payload = objectMapper.readValue(message.getBody(), AuditMessage.class);
        auditMapper.insert(payload.toEvent());
    }
}
