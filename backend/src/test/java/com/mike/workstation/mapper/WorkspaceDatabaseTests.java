package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 在显式指定的隔离数据库中验证中文、部分匹配和查询边界。 */
@EnabledIfSystemProperty(named = "workstation.test.db", matches = ".+")
class WorkspaceDatabaseTests {
    @Test
    void memberSearchMatchesNamesLiterallyAndReturnsAtMostTenUsers() throws Exception {
        String url = System.getProperty("workstation.test.db");
        Configuration configuration = new Configuration(new Environment("isolated",
                new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "")));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(WorkspaceMapper.class);
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            Connection db = session.getConnection();
            try (Statement statement = db.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO security_principals
                            (subject_id, principal_type, username, display_name, created_at, updated_at)
                        VALUES ('search-alex', 'USER', 'AlexChen', '张三', NOW(), NOW()),
                               ('search-service', 'SERVICE', 'AlexBot', '张机器人', NOW(), NOW()),
                               ('search-percent', 'USER', 'literal%_name', '符号', NOW(), NOW())
                        """);
                for (int index = 0; index < 12; index++) {
                    statement.executeUpdate("INSERT INTO security_principals VALUES ('search-limit-" + index
                            + "', 'USER', 'match-limit-" + index + "', NULL, NOW(), NOW())");
                }
            }
            WorkspaceMapper mapper = session.getMapper(WorkspaceMapper.class);
            assertThat(mapper.findUsersByUsername("aLeX"))
                    .extracting(user -> user.getSubjectId()).containsExactly("search-alex");
            assertThat(mapper.findUsersByUsername("张"))
                    .extracting(user -> user.getSubjectId()).containsExactly("search-alex");
            assertThat(mapper.findUsersByUsername("%_"))
                    .extracting(user -> user.getSubjectId()).containsExactly("search-percent");
            assertThat(mapper.findUsersByUsername("match-limit")).hasSize(10);
            assertThat(mapper.findUsersByUsername("不存在")).isEmpty();
            session.rollback();
        }
    }
}
