package com.mike.workstation.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 独立邀请引用队列，基础设施故障通过十秒延迟队列恢复。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "workstation.invitations", name = "enabled", havingValue = "true")
public class InvitationMessagingConfig {
    public static final String EXCHANGE = "workstation.invitation.exchange";
    public static final String QUEUE = "workstation.invitation.mail.queue";

    @Bean
    public Declarables invitationTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).quorum()
                .deadLetterExchange(EXCHANGE).deadLetterRoutingKey("retry")
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-delivery-limit", -1).build();
        var retry = QueueBuilder.durable("workstation.invitation.retry.queue").quorum()
                .ttl(10000).deadLetterExchange(EXCHANGE).deadLetterRoutingKey("send")
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-delivery-limit", -1).build();
        return new Declarables(exchange, queue, retry,
                BindingBuilder.bind(queue).to(exchange).with("send"),
                BindingBuilder.bind(retry).to(exchange).with("retry"));
    }
}
