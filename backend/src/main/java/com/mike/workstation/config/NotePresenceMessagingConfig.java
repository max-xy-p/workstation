package com.mike.workstation.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

/** 短期在线提醒不回放历史，实例断开后自动删除临时队列。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "workstation.note-presence", name = "enabled", havingValue = "true")
public class NotePresenceMessagingConfig {
    public static final String EXCHANGE = "workstation.note-presence.refresh.exchange";

    @Bean
    public FanoutExchange notePresenceExchange() {
        return new FanoutExchange(EXCHANGE, true, false);
    }

    @Bean
    public AnonymousQueue notePresenceQueue() {
        return new AnonymousQueue(Map.of("x-message-ttl", 10000, "x-max-length", 1024,
                "x-overflow", "drop-head"));
    }

    @Bean
    public Binding notePresenceBinding(@Qualifier("notePresenceQueue") AnonymousQueue queue,
            @Qualifier("notePresenceExchange") FanoutExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange);
    }
}
