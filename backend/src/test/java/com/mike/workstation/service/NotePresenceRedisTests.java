package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import com.mike.workstation.domain.NotePresenceState;
import com.mike.workstation.exception.NotePresenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** 仅使用显式隔离 Redis 及随机笔记 Key，覆盖独立实例和乱序离开。 */
@EnabledIfSystemProperty(named = "workstation.test.redis", matches = ".+")
class NotePresenceRedisTests {
    @Test
    void leasesMergeTabsRejectOldRequestsExpireAndBoundUserCapacity() throws Exception {
        String[] endpoint = System.getProperty("workstation.test.redis").split(":");
        var first = new LettuceConnectionFactory(endpoint[0], Integer.parseInt(endpoint[1]));
        var second = new LettuceConnectionFactory(endpoint[0], Integer.parseInt(endpoint[1]));
        first.afterPropertiesSet();
        second.afterPropertiesSet();
        var redis = new StringRedisTemplate(first);
        var a = new NotePresenceStore(redis, new ObjectMapper());
        var b = new NotePresenceStore(new StringRedisTemplate(second), new ObjectMapper());
        long workspace = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String prefix = "workstation:note-presence:{" + workspace + ":2}";
        var keys = List.of(prefix + ":expires", prefix + ":states");
        var pool = Executors.newFixedThreadPool(2);
        try {
            String tab = UUID.randomUUID().toString();
            String other = UUID.randomUUID().toString();
            assertThat(a.update(workspace, 2, "user", tab, 1, NotePresenceState.VIEWING)).isTrue();
            b.update(workspace, 2, "user", other, 1, NotePresenceState.EDITING);
            assertThat(a.snapshot(workspace, 2)).containsEntry("user", NotePresenceState.EDITING).hasSize(1);
            assertThat(a.snapshot(workspace + 1, 2)).isEmpty();
            var old = pool.submit(() -> a.update(workspace, 2, "user", tab, 2, NotePresenceState.EDITING));
            var left = pool.submit(() -> b.update(workspace, 2, "user", tab, 3, NotePresenceState.LEFT));
            old.get(5, TimeUnit.SECONDS);
            left.get(5, TimeUnit.SECONDS);
            assertThat(a.update(workspace, 2, "user", tab, 2, NotePresenceState.EDITING)).isFalse();
            assertThat(a.update(workspace, 2, "user", tab, 4, NotePresenceState.EDITING)).isFalse();
            b.update(workspace, 2, "user", other, 2, NotePresenceState.LEFT);
            assertThat(a.snapshot(workspace, 2)).isEmpty();
            String live = UUID.randomUUID().toString();
            b.update(workspace, 2, "fresh", live, 1, NotePresenceState.VIEWING);
            assertThat(redis.getExpire(keys.get(0), TimeUnit.SECONDS)).isBetween(145L, 150L);
            assertThat(redis.opsForZSet().score(keys.get(0), live + "|fresh"))
                    .isBetween((double) System.currentTimeMillis() + 40000, (double) System.currentTimeMillis() + 46000);
            // 将该随机测试租约移到过去，真实 Lua 读取必须清除状态和索引。
            redis.opsForZSet().add(keys.get(0), live + "|fresh", 1);
            assertThat(a.snapshot(workspace, 2)).isEmpty();
            assertThat(redis.opsForHash().hasKey(keys.get(1), live + "|fresh")).isFalse();
            for (int index = 0; index < 10; index++) {
                a.update(workspace, 2, "limited", UUID.randomUUID().toString(), 1, NotePresenceState.VIEWING);
            }
            assertThatThrownBy(() -> b.update(workspace, 2, "limited", UUID.randomUUID().toString(), 1, NotePresenceState.VIEWING))
                    .isInstanceOf(NotePresenceException.class);
            redis.expire(keys.get(0), Duration.ofMillis(1));
            redis.expire(keys.get(1), Duration.ofMillis(1));
            Thread.sleep(20);
            assertThat(a.snapshot(workspace, 2)).isEmpty();
        } finally {
            pool.shutdownNow();
            redis.delete(keys);
            first.destroy();
            second.destroy();
        }
    }
}
