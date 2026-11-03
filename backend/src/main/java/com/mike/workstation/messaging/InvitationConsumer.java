package com.mike.workstation.messaging;

import com.mike.workstation.config.InvitationMessagingConfig;
import com.mike.workstation.service.InvitationDeliveryService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 邮件服务确认持久化并完成本地状态提交后才返回，由容器确认 MQ 消息。 */
@Component
@ConditionalOnProperty(prefix = "workstation.invitations", name = "enabled", havingValue = "true")
public class InvitationConsumer {
    private final InvitationDeliveryService delivery;
    public InvitationConsumer(InvitationDeliveryService delivery) {
        this.delivery = delivery;
    }

    @RabbitListener(queues = InvitationMessagingConfig.QUEUE)
    public void receive(Message message) {
        if (message.getBody().length != 36) {
            return;
        }
        String id = new String(message.getBody(), StandardCharsets.US_ASCII);
        try {
            if (!UUID.fromString(id).toString().equals(id)) {
                return;
            }
        } catch (IllegalArgumentException exception) {
            return;
        }
        try {
            delivery.deliver(id);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpRejectAndDontRequeueException("邀请投递已中断");
        } catch (Exception exception) {
            throw new AmqpRejectAndDontRequeueException("邀请暂未完成投递");
        }
    }
}
