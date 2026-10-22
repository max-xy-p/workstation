package com.mike.workstation.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅在显式启用时启动 XXL-JOB 执行器，避免本地测试依赖调度中心。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workstation.xxl-job.enabled", havingValue = "true")
public class XxlJobConfig {
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.adminAddresses());
        executor.setTimeout(properties.adminTimeoutSeconds());
        executor.setEnabled(properties.enabled());
        executor.setAppname(properties.appName());
        executor.setAccessToken(properties.accessToken());
        executor.setAddress(properties.address());
        executor.setIp(properties.ip());
        executor.setPort(properties.port());
        executor.setLogPath(properties.logPath());
        executor.setLogRetentionDays(properties.logRetentionDays());
        return executor;
    }
}
