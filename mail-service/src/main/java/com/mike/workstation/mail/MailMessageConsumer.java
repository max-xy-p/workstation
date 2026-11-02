package com.mike.workstation.mail;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MailMessageConsumer {
    private final MailWorker worker;
    private final MailMessagePublisher publisher;
    private final ObjectMapper json;

    public MailMessageConsumer(MailWorker worker, MailMessagePublisher publisher, ObjectMapper json) {
        this.worker = worker;
        this.publisher = publisher;
        this.json = json;
    }

    @RabbitListener(queues = MailMessagingConfig.QUEUE)
    public void consume(Message message) {
        try {
            MailDispatch dispatch = parse(message);
            if (dispatch == null) {
                publisher.invalid();
                return;
            }
            MailDeliveryResult result = worker.dispatch(dispatch);
            if (result.delaySeconds() > 0) {
                // 延迟转发得到 Confirm 后，监听容器才 ACK 原消息。
                publisher.publish(dispatch, result.delaySeconds());
            } else if (result.failed()) {
                publisher.dead(dispatch);
            }
        } catch (RuntimeException error) {
            // 数据库异常或延迟转发失败时，Broker 至少一次转入十秒恢复队列，避免立即热循环。
            throw new AmqpRejectAndDontRequeueException("邮件消费暂时失败，等待延迟恢复");
        }
    }

    private MailDispatch parse(Message message) {
        try {
            return message.getBody().length <= 512
                    ? json.readValue(message.getBody(), MailDispatch.class) : null;
        } catch (RuntimeException error) {
            return null;
        }
    }
}
