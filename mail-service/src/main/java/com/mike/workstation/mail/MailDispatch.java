package com.mike.workstation.mail;

import java.util.UUID;

/** RabbitMQ 仅携带任务引用，不传输邮箱、验证码、正文或供应商凭据。 */
public record MailDispatch(String taskId, long version) {
    public MailDispatch {
        if (taskId == null || !UUID.fromString(taskId).toString().equals(taskId) || version < 1) {
            throw new IllegalArgumentException("邮件投递引用无效");
        }
    }
}
