package com.mike.workstation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 队列执行与定期触发使用不同线程，工作状态仍保存在数据库。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix="workstation.integrations",name="enabled",havingValue="true")
public class IntegrationSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler integrationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("integration-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
