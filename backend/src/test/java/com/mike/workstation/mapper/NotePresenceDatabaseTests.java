package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/** 真实 SQL 只返回目标空间当前的人类成员，不依赖 Redis 中的历史资格。 */
@EnabledIfSystemProperty(named = "workstation.test.db", matches = ".+")
class NotePresenceDatabaseTests {
    @Test
    void batchProjectionFiltersMachinesRemovedMembersAndOtherWorkspaces() throws Exception {
        var configuration = new Configuration(new Environment("presence", new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getProperty("workstation.test.db"), "root", "")));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NotePresenceMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var db = session.getConnection();
            String user = "presence-" + UUID.randomUUID();
            String machine = "presence-" + UUID.randomUUID();
            try (var insert = db.prepareStatement("INSERT INTO security_principals VALUES (?,?,'在线成员',NULL,NOW(),NOW())")) {
                insert.setString(1, user);
                insert.setString(2, "USER");
                insert.executeUpdate();
                insert.setString(1, machine);
                insert.setString(2, "SERVICE");
                insert.executeUpdate();
            }
            long workspace;
            try (var insert = db.prepareStatement("INSERT INTO workspaces (name,type,created_by_subject,created_at,updated_at) VALUES ('在线测试','SHARED',?,NOW(),NOW())", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, user);
                insert.executeUpdate();
                try (var keys = insert.getGeneratedKeys()) {
                    keys.next();
                    workspace = keys.getLong(1);
                }
            }
            try (var insert = db.prepareStatement("INSERT INTO workspace_members VALUES (?,?,'VIEWER',NOW())")) {
                insert.setLong(1, workspace);
                insert.setString(2, user);
                insert.executeUpdate();
                insert.setString(2, machine);
                insert.executeUpdate();
            }
            var mapper = session.getMapper(NotePresenceMapper.class);
            assertThat(mapper.currentMembers(workspace, List.of(user, machine))).extracting(m -> m.getSubjectId()).containsExactly(user);
            assertThat(mapper.currentMembers(workspace + 1, List.of(user))).isEmpty();
            try (var delete = db.prepareStatement("DELETE FROM workspace_members WHERE workspace_id=? AND principal_subject=?")) {
                delete.setLong(1, workspace);
                delete.setString(2, user);
                delete.executeUpdate();
            }
            session.clearCache();
            assertThat(mapper.currentMembers(workspace, List.of(user, machine))).isEmpty();
            session.rollback();
        }
    }
}
