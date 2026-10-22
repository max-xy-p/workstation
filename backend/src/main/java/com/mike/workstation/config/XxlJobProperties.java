package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Workstation Backend 作为 XXL-JOB 执行器时使用的配置。 */
@ConfigurationProperties("workstation.xxl-job")
public record XxlJobProperties(
        boolean enabled,
        String adminAddresses,
        int adminTimeoutSeconds,
        String appName,
        String accessToken,
        String address,
        String ip,
        int port,
        String logPath,
        int logRetentionDays) {}
