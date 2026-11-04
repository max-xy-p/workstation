package com.mike.workstation.service;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.exception.NotificationStreamLimitException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 本节点的有界 SSE 连接，仅发送无业务内容的刷新信号与心跳。 */
@Component
public class NotificationStreamHub {
    private final NotificationProperties properties;
    private final ConcurrentHashMap<String, Set<Client>> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor writes = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1024));
    private int total;

    public NotificationStreamHub(NotificationProperties properties) {
        this.properties = properties;
        if (properties.enabled()) {
            scheduler.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
        }
    }

    public synchronized SseEmitter open(String subject, Instant tokenExpiresAt) {
        long lifetime = tokenExpiresAt == null ? 0 : Math.min(55000, Duration.between(Instant.now(), tokenExpiresAt).toMillis());
        if (lifetime <= 0) {
            throw new WorkspaceAccessDeniedException("通知连接需要有效的用户令牌");
        }
        Set<Client> current = clients.computeIfAbsent(subject, ignored -> ConcurrentHashMap.newKeySet());
        if (total >= properties.maxConnections() || current.size() >= properties.connectionsPerUser()) {
            if (current.isEmpty()) {
                clients.remove(subject, current);
            }
            throw new NotificationStreamLimitException();
        }
        Client client = new Client(subject, new SseEmitter(lifetime));
        current.add(client);
        total++;
        client.emitter.onCompletion(() -> remove(client));
        client.emitter.onError(error -> remove(client));
        client.emitter.onTimeout(() -> close(client));
        scheduler.schedule(() -> close(client), lifetime, TimeUnit.MILLISECONDS);
        enqueue(client, true);
        return client.emitter;
    }

    public void changed(String subject) {
        Set<Client> current = clients.get(subject);
        if (current != null) {
            current.forEach(client -> enqueue(client, true));
        }
    }

    private void heartbeat() {
        clients.values().forEach(current -> current.forEach(client -> enqueue(client, false)));
    }

    private void enqueue(Client client, boolean refresh) {
        if (!client.open.get()) {
            return;
        }
        if (refresh) {
            client.refresh.set(true);
        }
        if (!client.pending.compareAndSet(false, true)) {
            return;
        }
        try {
            writes.execute(() -> send(client));
        } catch (RejectedExecutionException exception) {
            close(client);
        }
    }

    private void send(Client client) {
        try {
            if (client.open.get()) {
                if (client.refresh.getAndSet(false)) {
                    client.emitter.send(SseEmitter.event().name("refresh").reconnectTime(3000).data("{}"));
                } else {
                    client.emitter.send(SseEmitter.event().comment("keepalive"));
                }
            }
        } catch (Exception exception) {
            close(client);
        } finally {
            client.pending.set(false);
            if (client.open.get() && client.refresh.get()) {
                enqueue(client, true);
            }
        }
    }

    private synchronized void remove(Client client) {
        if (client.open.compareAndSet(true, false)) {
            Set<Client> current = clients.get(client.subject);
            if (current != null) {
                current.remove(client);
                if (current.isEmpty()) {
                    clients.remove(client.subject, current);
                }
            }
            total--;
        }
    }

    private void close(Client client) {
        remove(client);
        client.emitter.complete();
    }

    @PreDestroy
    public void shutdown() {
        clients.values().forEach(current -> current.forEach(this::close));
        scheduler.shutdownNow();
        writes.shutdownNow();
    }

    private static final class Client {
        private final String subject;
        private final SseEmitter emitter;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicBoolean pending = new AtomicBoolean();
        private final AtomicBoolean refresh = new AtomicBoolean();

        private Client(String subject, SseEmitter emitter) {
            this.subject = subject;
            this.emitter = emitter;
        }
    }
}
