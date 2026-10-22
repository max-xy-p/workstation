package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首页概览配置。
 *
 * @param recentLimit 首页最近项目、最近笔记的展示条数
 */
@ConfigurationProperties(prefix = "workstation.dashboard")
public record DashboardProperties(Integer recentLimit) {}
