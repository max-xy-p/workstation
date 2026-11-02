package com.mike.workstation.mail;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MailOutboxRepository {
    private final JdbcTemplate jdbc;

    public MailOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(String id, MailDispatch dispatch, long delaySeconds, Instant createdAt,
                        Instant expiresAt, String lease) { }

    @Transactional
    public Entry claim(Instant now) {
        List<Entry> rows = jdbc.query("""
                SELECT * FROM mail_outbox WHERE (state = 'PENDING' AND next_attempt_at <= ?)
                OR (state = 'SENDING' AND lease_until <= ?) ORDER BY created_at, id LIMIT 1 FOR UPDATE
                """, (rs, row) -> new Entry(rs.getString("id"),
                new MailDispatch(rs.getString("task_id"), rs.getLong("dispatch_version")),
                rs.getLong("delay_seconds"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(), null), Timestamp.from(now), Timestamp.from(now));
        if (rows.isEmpty()) {
            return null;
        }
        Entry entry = rows.get(0);
        String lease = UUID.randomUUID().toString();
        jdbc.update("UPDATE mail_outbox SET state = 'SENDING', lease_token = ?, lease_until = ? WHERE id = ?",
                lease, Timestamp.from(now.plusSeconds(30)), entry.id());
        return new Entry(entry.id(), entry.dispatch(), entry.delaySeconds(), entry.createdAt(), entry.expiresAt(), lease);
    }

    public void published(Entry entry, boolean expired) {
        jdbc.update("""
                UPDATE mail_outbox SET state = ?, lease_token = NULL, lease_until = NULL
                WHERE id = ? AND lease_token = ? AND state = 'SENDING'
                """, expired ? "CANCELLED" : "PUBLISHED", entry.id(), entry.lease());
    }

    public void failed(Entry entry, Instant now) {
        jdbc.update("""
                UPDATE mail_outbox SET state = 'PENDING', next_attempt_at = ?, lease_token = NULL, lease_until = NULL
                WHERE id = ? AND lease_token = ? AND state = 'SENDING'
                """, Timestamp.from(now.plusSeconds(10)), entry.id(), entry.lease());
    }
}
