package com.mike.workstation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 每个实例独立持久化队列接收广播，消费失败只重试本实例的队列。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "workstation.notifications", name = "enabled", havingValue = "true")
public class NotificationMessagingConfig {
    public static final String EXCHANGE = "workstation.notification.refresh.exchange";
    public static final String RETRY_EXCHANGE = "workstation.notification.refresh.retry.exchange";

    public static String queueName(String instanceId) {
        return "workstation.notification.refresh." + instanceId + ".queue";
    }

    @Bean
    public Queue notificationRefreshQueue(NotificationProperties properties) {
        return mainQueue(properties.instanceId());
    }

    public static Queue mainQueue(String instanceId) {
        return queue(queueName(instanceId), instanceId + ".delay")
                .withArgument("x-single-active-consumer", true).build();
    }

    public static Queue retryQueue(String instanceId) {
        return queue(queueName(instanceId) + ".retry", instanceId + ".deliver").ttl(10000).build();
    }

    private static QueueBuilder queue(String name, String deadRoute) {
        return QueueBuilder.durable(name).quorum()
                .deadLetterExchange(RETRY_EXCHANGE).deadLetterRoutingKey(deadRoute)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-delivery-limit", -1);
    }

    @Bean
    public Declarables notificationTopology(NotificationProperties properties,
            @Qualifier("notificationRefreshQueue") Queue notificationRefreshQueue) {
        return topology(properties.instanceId(), notificationRefreshQueue);
    }

    public static Declarables topology(String instanceId, Queue main) {
        var exchange = new FanoutExchange(EXCHANGE, true, false);
        var retryExchange = new DirectExchange(RETRY_EXCHANGE, true, false);
        Queue retry = retryQueue(instanceId);
        Binding broadcast = BindingBuilder.bind(main).to(exchange);
        Binding redelivery = BindingBuilder.bind(main).to(retryExchange).with(instanceId + ".deliver");
        Binding delay = BindingBuilder.bind(retry).to(retryExchange).with(instanceId + ".delay");
        return new Declarables(exchange, retryExchange, retry, broadcast, redelivery, delay);
    }
}
