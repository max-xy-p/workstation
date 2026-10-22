package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/** 数据源功能与出站访问配置。 */
@ConfigurationProperties(prefix = "workstation.integrations")
public record IntegrationProperties(
        boolean enabled,
        String keyVersion,
        Map<String, String> encryptionKeys,
        List<String> allowedGitlabInstances,
        int requestTimeoutSeconds,
        int maxPages,
        int maxItems,
        int leaseSeconds,
        int recentDays) {
    public IntegrationProperties {
        if (requestTimeoutSeconds < 1
                || requestTimeoutSeconds > 60
                || maxPages < 1
                || maxPages > 1000
                || maxItems < 1
                || maxItems > 1000
                || leaseSeconds < requestTimeoutSeconds * 3 + 10
                || leaseSeconds > 3600
                || recentDays < 1
                || recentDays > 365) {
            throw new IllegalArgumentException("数据源请求、分页、租约或采集范围配置不合法");
        }
    }
}
