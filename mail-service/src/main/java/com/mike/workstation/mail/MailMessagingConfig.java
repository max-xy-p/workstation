package com.mike.workstation.mail;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MailMessagingConfig {
    public static final String EXCHANGE = "workstation.mail.exchange";
    public static final String QUEUE = "workstation.mail.send.queue";
    public static final String SEND = "mail.send";
    public static final String DEAD = "mail.dead";
    public static final String DEAD_QUEUE = "workstation.mail.dead.queue";
    static final List<Long> DELAYS = List.of(1L, 10L, 60L, 300L, 900L, 3600L);

    @Bean
    Declarables mailTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        List<Declarable> elements = new ArrayList<>();
        elements.add(exchange);
        Queue main = reliableQueue(QUEUE, "mail.retry.10").build();
        elements.add(main);
        elements.add(bind(main, exchange, SEND));
        Queue dead = QueueBuilder.durable(DEAD_QUEUE).quorum()
                .withArgument("x-message-ttl", 7L * 86400 * 1000).build();
        elements.add(dead);
        elements.add(bind(dead, exchange, DEAD));
        for (long delay : DELAYS) {
            Queue retry = reliableQueue("workstation.mail.retry." + delay + ".queue", SEND)
                    .withArgument("x-message-ttl", delay * 1000).build();
            elements.add(retry);
            elements.add(bind(retry, exchange, "mail.retry." + delay));
        }
        return new Declarables(elements);
    }

    private QueueBuilder reliableQueue(String name, String destination) {
        // 延迟队列也使用至少一次死信转发，目标暂时不可用时由 Broker 保留并重试。
        return QueueBuilder.durable(name).quorum().deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(destination)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-delivery-limit", -1);
    }

    private Binding bind(Queue queue, DirectExchange exchange, String routing) {
        return BindingBuilder.bind(queue).to(exchange).with(routing);
    }

    static String route(long seconds) {
        if (seconds <= 0) {
            return SEND;
        }
        for (long delay : DELAYS) {
            if (delay >= seconds) {
                return "mail.retry." + delay;
            }
        }
        // 长冷却先延迟一小时，消费者按持久化的 next_attempt_at 再判断，不提前发信。
        return "mail.retry.3600";
    }
}
