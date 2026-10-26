package com.mike.workstation.integration;

import static org.assertj.core.api.Assertions.*;

import com.mike.workstation.config.ExternalApiLimitProperties;
import com.mike.workstation.exception.ExternalApiDeferredException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 仅连接显式指定的隔离 Redis；随机 Key 且只清理本测试创建的数据。 */
@EnabledIfSystemProperty(named = "workstation.test.redis", matches = ".+")
class ExternalApiRedisTests {
    @Test
    void multipleExecutorsShareAtomicBudgetCooldownAndExpiry() throws Exception {
        String[] endpoint = System.getProperty("workstation.test.redis").split(":");
        var first = new LettuceConnectionFactory(endpoint[0], Integer.parseInt(endpoint[1]));
        var second = new LettuceConnectionFactory(endpoint[0], Integer.parseInt(endpoint[1]));
        first.afterPropertiesSet();
        second.afterPropertiesSet();
        var redis = new StringRedisTemplate(first);
        var other = new StringRedisTemplate(second);
        var properties = new ExternalApiLimitProperties(1, 1, 60, 30);
        var a = new ExternalApiLimiter(redis, properties);
        var b = new ExternalApiLimiter(other, properties);
        String key = "workstation:test:external-api:" + UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(16);
        try {
            // 预热连接，计时窗口内的所有申请必须符合共享令牌桶预算。
            redis.hasKey(key);
            other.hasKey(key);
            long begun = System.nanoTime();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> calls = new ArrayList<>();
            for (int n = 0; n < 32; n++) {
                ExternalApiLimiter target = n % 2 == 0 ? a : b;
                calls.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        target.acquire(key);
                                        return true;
                                    } catch (ExternalApiDeferredException ex) {
                                        return false;
                                    }
                                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> call : calls) {
                if (call.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begun);
            assertThat(accepted).isBetween(1, 1 + (int) (elapsedMillis / 1000));
            a.cooldown(key, Duration.ofSeconds(3));
            assertThat(b.cooldown(key, Duration.ofMillis(100)).toMillis()).isGreaterThan(2000);
            assertThatThrownBy(() -> b.acquire(key))
                    .isInstanceOf(ExternalApiDeferredException.class);
            assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(2000L, 5000L);
            // 过期或状态丢失后重新建桶，任务真源不依赖 Redis Key 存活。
            redis.expire(key, Duration.ofMillis(1));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
                Thread.yield();
            }
            a.acquire(key);
            assertThatThrownBy(() -> b.acquire(key))
                    .isInstanceOf(ExternalApiDeferredException.class);
        } finally {
            pool.shutdownNow();
            redis.delete(key);
            first.destroy();
            second.destroy();
        }
    }
}
