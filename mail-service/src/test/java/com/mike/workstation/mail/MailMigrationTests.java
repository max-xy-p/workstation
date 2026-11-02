package com.mike.workstation.mail;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "workstation.mail.test.db",
        matches = "jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/workstation_mail_test(?:[?].*)?")
class MailMigrationTests {
    @Test
    void migrationTakesOverOnlyPendingLegacyMailAndCanBeRepeated() throws Exception {
        String url = System.getProperty("workstation.mail.test.db");
        Properties credentials = new Properties();
        credentials.setProperty("user", "root");
        credentials.setProperty("password", System.getenv().getOrDefault("MAIL_TEST_DB_PASSWORD", ""));
        String database = "workstation_mail_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection server = DriverManager.getConnection(url, credentials);
             Statement control = server.createStatement()) {
            control.execute("CREATE DATABASE " + database);
            try (Connection isolated = DriverManager.getConnection(url.replace("/workstation_mail_test", "/" + database), credentials);
                 Statement sql = isolated.createStatement()) {
                String fresh;
                try (var input = getClass().getResourceAsStream("/schema.sql")) {
                    assertNotNull(input);
                    fresh = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                }
                String legacy = fresh.split("CREATE TABLE IF NOT EXISTS mail_outbox")[0]
                        .replace("    dispatch_version BIGINT NOT NULL DEFAULT 1,\n", "");
                execute(sql, legacy);
                String pending = insert(isolated, "QUEUED", false);
                String sending = insert(isolated, "SENDING", false);
                insert(isolated, "SUBMITTED", false);
                insert(isolated, "QUEUED", true);
                String migration = Files.readString(Path.of("migrate-rabbitmq.sql"));
                execute(sql, migration);
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM mail_outbox"));
                assertEquals("QUEUED", scalar(sql, "SELECT status FROM mail_messages WHERE id = '" + sending + "'"));
                assertEquals("2", scalar(sql, "SELECT attempts FROM mail_messages WHERE id = '" + pending + "'"));
                sql.execute("UPDATE mail_messages SET status = 'SENDING', lease_token = 'new-consumer' WHERE id = '" + sending + "'");
                execute(sql, migration);
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM mail_outbox"));
                assertEquals("new-consumer", scalar(sql, "SELECT lease_token FROM mail_messages WHERE id = '" + sending + "'"));
                // 与全新初始化组合后仍保持幂等，证明新旧路径可衔接。
                execute(sql, fresh);
                assertEquals("4", scalar(sql, "SELECT COUNT(*) FROM mail_messages"));
            } finally {
                // 只删除本方法刚创建的随机隔离数据库，不复用或清理部署数据库。
                control.execute("DROP DATABASE " + database);
            }
        }
    }

    private String insert(Connection connection, String state, boolean expired) throws Exception {
        String id = UUID.randomUUID().toString();
        try (var statement = connection.prepareStatement("""
                INSERT INTO mail_messages (id, caller, request_key, fingerprint, payload, status,
                created_at, expires_at, next_attempt_at, attempts) VALUES (?, 'keycloak', ?, 'fixture', 'encrypted-fixture', ?, ?, ?, ?, 2)
                """)) {
            statement.setString(1, id);
            statement.setString(2, id);
            statement.setString(3, state);
            statement.setTimestamp(4, java.sql.Timestamp.from(Instant.now()));
            statement.setTimestamp(5, java.sql.Timestamp.from(Instant.now().plusSeconds(expired ? -60 : 300)));
            statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
        return id;
    }

    private void execute(Statement statement, String script) throws Exception {
        for (String command : script.split(";")) {
            if (!command.isBlank()) {
                statement.execute(command);
            }
        }
    }

    private String scalar(Statement statement, String query) throws Exception {
        try (var result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
