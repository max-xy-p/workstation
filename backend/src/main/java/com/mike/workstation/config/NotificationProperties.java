package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** 通知中心开关、节点连接上限与数据保留周期。 */
@ConfigurationProperties(prefix = "workstation.notifications")
public record NotificationProperties(boolean enabled, int maxConnections,
        int connectionsPerUser, int retentionDays, String instanceId) {
    public NotificationProperties(boolean enabled, int maxConnections, int connectionsPerUser, int retentionDays) {
        this(enabled, maxConnections, connectionsPerUser, retentionDays, "backend");
    }

    @ConstructorBinding
    public NotificationProperties {
        instanceId = instanceId == null ? "backend" : instanceId;
        if (!instanceId.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("通知实例标识不合法");
        }
        if (maxConnections < 1 || maxConnections > 10000 || connectionsPerUser < 1
                || connectionsPerUser > 20 || connectionsPerUser > maxConnections
                || retentionDays < 8 || retentionDays > 365) {
            throw new IllegalArgumentException("通知连接上限或保留周期不合法");
        }
    }
}
