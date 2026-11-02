package com.mike.workstation.mail;

/** delaySeconds 大于零表示由 RabbitMQ 延迟后再处理，failed 表示隔离到死信队列。 */
public record MailDeliveryResult(long delaySeconds, boolean failed) {
    static MailDeliveryResult done() {
        return new MailDeliveryResult(0, false);
    }
}
