package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workstation.dashboard")
public record DashboardProperties(Integer recentLimit) {
}
