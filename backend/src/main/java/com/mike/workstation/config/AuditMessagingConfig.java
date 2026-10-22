package com.mike.workstation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计事件的 RabbitMQ 主队列和死信队列拓扑。
 *
 * <p>正常消息进入主队列；消费者多次失败并拒绝消息后，RabbitMQ 根据主队列上的 DLX 参数 将消息转入死信队列，避免有问题的消息无限立即重试。
 */
@Configuration(proxyBeanMethods = false)
public class AuditMessagingConfig {
    public static final String EXCHANGE = "workstation.audit.exchange";
    public static final String ROUTING_KEY = "audit.persist";
    public static final String QUEUE = "workstation.audit.persist.queue";
    public static final String DEAD_LETTER_EXCHANGE = "workstation.audit.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "audit.dead";
    public static final String DEAD_LETTER_QUEUE = "workstation.audit.dead.queue";

    @Bean
    DirectExchange auditExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue auditQueue() {
        // 死信参数写在主队列上，由 Broker 在消息被拒绝时执行转发。
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding auditBinding(
            @Qualifier("auditExchange") DirectExchange auditExchange,
            @Qualifier("auditQueue") Queue auditQueue) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(ROUTING_KEY);
    }

    @Bean
    DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding auditDeadLetterBinding(
            @Qualifier("auditDeadLetterExchange") DirectExchange auditDeadLetterExchange,
            @Qualifier("auditDeadLetterQueue") Queue auditDeadLetterQueue) {
        return BindingBuilder.bind(auditDeadLetterQueue)
                .to(auditDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
