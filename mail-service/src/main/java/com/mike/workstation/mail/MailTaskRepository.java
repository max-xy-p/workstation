package com.mike.workstation.mail;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MailTaskRepository {
    private final JdbcTemplate jdbc;

    public MailTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MailTask byKey(String caller, String key) {
        return first(jdbc.query("SELECT * FROM mail_messages WHERE caller = ? AND request_key = ?",
                this::map, caller, key));
    }

    public MailTask byId(String caller, String id) {
        return first(jdbc.query("SELECT * FROM mail_messages WHERE caller = ? AND id = ?", this::map, caller, id));
    }

    public void insert(String id, String caller, MailRequest request, String fingerprint, String payload, Instant now) {
        jdbc.update("""
                INSERT INTO mail_messages
                (id, caller, request_key, fingerprint, payload, status, created_at, expires_at, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?)
                """, id, caller, request.requestKey(), fingerprint, payload, timestamp(now),
                timestamp(request.expiresAt()), timestamp(now));
    }

    @Transactional
    public MailTask claim(Instant now) {
        // 所有实例先锁定同一时钟行，限制整个 Resend 账号的发送速率，领取后立即释放事务。
        Instant next = jdbc.queryForObject("SELECT next_at FROM mail_dispatch_clock WHERE id = 1 FOR UPDATE",
                (rs, row) -> rs.getTimestamp(1).toInstant());
        if (next != null && next.isAfter(now)) {
            return null;
        }
        jdbc.update("""
                UPDATE mail_messages SET status = 'EXPIRED', payload = NULL, lease_token = NULL, lease_until = NULL
                WHERE expires_at <= ? AND status IN ('QUEUED', 'SENDING')
                """, timestamp(now));
        List<MailTask> tasks = jdbc.query("""
                SELECT * FROM mail_messages WHERE expires_at > ? AND
                ((status = 'QUEUED' AND next_attempt_at <= ?) OR (status = 'SENDING' AND lease_until <= ?))
                ORDER BY created_at, id LIMIT 1 FOR UPDATE
                """, this::map, timestamp(now), timestamp(now), timestamp(now));
        if (tasks.isEmpty()) {
            return null;
        }
        MailTask task = tasks.get(0);
        String lease = UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE mail_messages SET status = 'SENDING', attempts = attempts + 1, lease_token = ?, lease_until = ?
                WHERE id = ?
                """, lease, timestamp(now.plusSeconds(60)), task.id());
        jdbc.update("UPDATE mail_dispatch_clock SET next_at = ? WHERE id = 1", timestamp(now.plusSeconds(1)));
        return new MailTask(task.id(), task.caller(), task.fingerprint(), task.payload(), "SENDING",
                task.attempts() + 1, task.expiresAt(), lease, task.providerId(), task.errorCode());
    }

    @Transactional
    public void finish(MailTask task, String status, String providerId, String code) {
        jdbc.update("""
                UPDATE mail_messages SET status = ?, provider_id = ?, error_code = ?, payload = NULL,
                lease_token = NULL, lease_until = NULL WHERE id = ? AND lease_token = ? AND status = 'SENDING'
                """, status, providerId, code, task.id(), task.leaseToken());
        if (providerId != null) {
            applyLatestEvent(providerId);
        }
    }

    @Transactional
    public void retry(MailTask task, Instant next, String code) {
        jdbc.update("""
                UPDATE mail_messages SET status = 'QUEUED', next_attempt_at = ?, error_code = ?,
                lease_token = NULL, lease_until = NULL WHERE id = ? AND lease_token = ? AND status = 'SENDING'
                """, timestamp(next), code, task.id(), task.leaseToken());
        jdbc.update("UPDATE mail_dispatch_clock SET next_at = ? WHERE id = 1 AND next_at < ?",
                timestamp(next), timestamp(next));
    }

    @Transactional
    public void purge(Instant now) {
        // 即使限流或无任务可发，也及时擦除到期正文；只保留有限期的投递元数据。
        jdbc.update("""
                UPDATE mail_messages SET payload = NULL, status = 'EXPIRED', lease_token = NULL, lease_until = NULL
                WHERE expires_at <= ? AND status IN ('QUEUED', 'SENDING')
                """, timestamp(now));
        jdbc.update("DELETE FROM mail_messages WHERE expires_at < ?", timestamp(now.minusSeconds(30L * 86400)));
        jdbc.update("DELETE FROM mail_delivery_events WHERE received_at < ?", timestamp(now.minusSeconds(30L * 86400)));
    }

    @Transactional
    public void delivery(String eventId, String providerId, String status, Instant occurredAt, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO mail_delivery_events (event_id, provider_id, status, occurred_at, received_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, eventId, providerId, status, timestamp(occurredAt), timestamp(now));
        } catch (DuplicateKeyException ignored) {
            // 回调会重复投递，仅保存一次；仍尝试应用，兼容先于发送响应到达的回调。
        }
        applyLatestEvent(providerId);
    }

    private void applyLatestEvent(String providerId) {
        jdbc.query("""
                SELECT status, occurred_at FROM mail_delivery_events
                WHERE provider_id = ? ORDER BY occurred_at DESC, event_id DESC LIMIT 1
                """, rs -> {
                    jdbc.update("""
                            UPDATE mail_messages SET status = ?, delivery_at = ? WHERE provider_id = ?
                            AND (delivery_at IS NULL OR delivery_at <= ?)
                            """, rs.getString("status"), rs.getTimestamp("occurred_at"), providerId, rs.getTimestamp("occurred_at"));
                }, providerId);
    }

    private MailTask first(List<MailTask> tasks) {
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private MailTask map(ResultSet rs, int row) throws SQLException {
        return new MailTask(rs.getString("id"), rs.getString("caller"), rs.getString("fingerprint"),
                rs.getString("payload"), rs.getString("status"), rs.getInt("attempts"),
                rs.getTimestamp("expires_at").toInstant(), rs.getString("lease_token"),
                rs.getString("provider_id"), rs.getString("error_code"));
    }
}
