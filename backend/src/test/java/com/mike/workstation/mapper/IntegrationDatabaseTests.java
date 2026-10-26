package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.*;

import com.mike.workstation.domain.DevelopmentItem;
import com.mike.workstation.domain.DevelopmentSnapshot;
import com.mike.workstation.domain.ExternalRepository;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IntegrationRun;
import com.mike.workstation.domain.IntegrationRunItem;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;

/** 显式提供隔离数据库地址时执行真实 MySQL 约束和 Mapper 查询，不连接部署数据库。 */
@EnabledIfSystemProperty(named = "workstation.test.db", matches = ".+")
class IntegrationDatabaseTests {
    @Test
    void mysqlMappersPreserveWorkspaceIdentityLeasesAndProjectRelations() throws Exception {
        String url = System.getProperty("workstation.test.db");
        try (Connection db = DriverManager.getConnection(url, "root", "");
                Statement s = db.createStatement()) {
            s.executeUpdate(
                    "INSERT INTO security_principals VALUES"
                        + " ('integration-test','USER','test',NULL,NOW(),NOW())");
            s.executeUpdate(
                    "INSERT INTO workspaces (id,name,type,created_by_subject,created_at,updated_at)"
                        + " VALUES"
                        + " (91,'隔离测试','SHARED','integration-test',NOW(),NOW()),(92,'其他空间','SHARED','integration-test',NOW(),NOW())");
        }
        Configuration cfg =
                new Configuration(
                        new Environment(
                                "isolated",
                                new JdbcTransactionFactory(),
                                new UnpooledDataSource(
                                        "com.mysql.cj.jdbc.Driver", url, "root", "")));
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(IntegrationMapper.class);
        cfg.addMapper(ProjectMapper.class);
        try (SqlSession session = new SqlSessionFactoryBuilder().build(cfg).openSession()) {
            IntegrationMapper m = session.getMapper(IntegrationMapper.class);
            ProjectMapper p = session.getMapper(ProjectMapper.class);
            IntegrationConnection c = new IntegrationConnection();
            c.setWorkspaceId(91L);
            c.setName("test");
            c.setProvider("GITHUB");
            c.setBaseUrl("https://github.com");
            c.setAccount("test");
            c.setCredential("encrypted");
            c.setKeyVersion("v1");
            c.setScope("OWNED");
            c.setOwnerSubject("integration-test");
            c.setEnabled(true);
            c.setVersion(1);
            m.insertConnection(c);
            assertThat(m.connection(92L, c.getId())).isNull();
            assertThat(m.connections(91L, 0, 20)).hasSize(1);
            assertThat(m.lockConnection(91L, c.getId()).getVersion()).isEqualTo(1);
            ExternalRepository r = new ExternalRepository();
            r.setWorkspaceId(91L);
            r.setConnectionId(c.getId());
            r.setProvider("GITHUB");
            r.setBaseUrl("https://github.com");
            r.setExternalId("123");
            r.setNamespace("test");
            r.setName("repo");
            r.setUrl("https://github.com/test/repo");
            r.setVisibility("private");
            m.insertRepository(r);
            assertThat(m.source(r).getId()).isEqualTo(r.getId());
            r.setName("renamed");
            m.updateRepository(r);
            Project project = new Project();
            project.setWorkspaceId(91L);
            project.setName("本地项目");
            project.setStatus(ProjectStatus.ACTIVE);
            project.setHomepageUrl("https://example.test");
            project.setCreatedAt(LocalDateTime.now());
            project.setUpdatedAt(LocalDateTime.now());
            p.insert(project);
            m.bind(91L, project.getId(), r.getId());
            assertThat(m.repository(91L, r.getId()).getProjectId()).isEqualTo(project.getId());
            assertThat(m.repositories(91L, c.getId(), -1L, 0, 20)).hasSize(1);
            assertThat(m.countRepositories(91L, null, project.getId())).isEqualTo(1);
            IntegrationRun run = new IntegrationRun();
            run.setWorkspaceId(91L);
            run.setConnectionId(c.getId());
            run.setKind("IMPORT");
            run.setStatus("QUEUED");
            run.setRequestKey("request");
            run.setRequestHash("hash");
            run.setActorSubject("integration-test");
            run.setConfigVersion(1);
            run.setCreatedAt(LocalDateTime.now());
            m.insertRun(run);
            assertThat(m.nextRun().getId()).isEqualTo(run.getId());
            run.setStatus("RUNNING");
            run.setLeaseToken("lease");
            run.setLeaseUntil(LocalDateTime.now().plusMinutes(3));
            m.claim(run);
            assertThat(m.lockRun(run)).isNotNull();
            assertThat(m.nextRun()).isNull();
            run.setProgressJson("{\"discoveryPage\":2}");
            run.setIssueCursor(17);
            assertThat(m.updateRun(run)).isEqualTo(1);
            run.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
            run.setLastError("平台正在冷却");
            assertThat(m.deferRun(run)).isEqualTo(1);
            IntegrationRun queued = m.run(91L, c.getId(), run.getId());
            assertThat(queued.getStatus()).isEqualTo("QUEUED");
            assertThat(queued.getLeaseToken()).isNull();
            assertThat(queued.getProgressJson()).contains("discoveryPage");
            assertThat(queued.getIssueCursor()).isEqualTo(17);
            assertThat(m.nextRun()).isNull();
            assertThat(m.lockRun(run)).isNull();
            assertThat(m.deferRun(run)).isZero();
            try (Statement ready = session.getConnection().createStatement()) {
                ready.executeUpdate(
                        "UPDATE integration_sync_runs SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 1"
                            + " SECOND) WHERE id="
                                + run.getId());
            }
            IntegrationRun resumed = m.nextRun();
            assertThat(resumed.getId()).isEqualTo(run.getId());
            assertThat(resumed.getProgressJson()).isEqualTo(queued.getProgressJson());
            resumed.setLeaseToken("new-lease");
            resumed.setLeaseUntil(LocalDateTime.now().plusMinutes(3));
            assertThat(m.claim(resumed)).isEqualTo(1);
            assertThat(m.updateRun(run)).isZero();
            run.setLeaseToken("new-lease");
            run.setNextAttemptAt(null);
            IntegrationRunItem item = new IntegrationRunItem();
            item.setWorkspaceId(91L);
            item.setRunId(run.getId());
            item.setRepositoryId(r.getId());
            item.setProjectStatus("PLANNING");
            m.insertItem(item);
            assertThat(m.items(91L, run.getId(), 0, 20)).hasSize(1);
            DevelopmentSnapshot snapshot = new DevelopmentSnapshot();
            snapshot.setWorkspaceId(91L);
            snapshot.setRepositoryId(r.getId());
            snapshot.setKind("ISSUE");
            snapshot.setAvailable(true);
            snapshot.setSince(LocalDateTime.now().minusDays(30));
            snapshot.setSyncedAt(LocalDateTime.now());
            snapshot.setCollected(1);
            snapshot.setOpenCount(1);
            m.snapshot(snapshot);
            DevelopmentItem development = new DevelopmentItem();
            development.setWorkspaceId(91L);
            development.setRepositoryId(r.getId());
            development.setKind("ISSUE");
            development.setExternalId("1");
            development.setTitle("问题");
            development.setStatus("OPEN");
            m.insertDevelopment(development);
            assertThat(m.development(91L, project.getId(), "ISSUE", 0, 20)).hasSize(1);
            assertThat(m.snapshots(91L, project.getId())).hasSize(1);
            assertThat(p.findById(91L, project.getId()).getHomepageUrl())
                    .isEqualTo("https://example.test");
            assertThat(m.repository(91L, r.getId()).getCollectedOpenIssues()).isEqualTo(1);
            assertThatThrownBy(() -> m.bind(92L, project.getId(), r.getId()))
                    .isInstanceOf(PersistenceException.class);
            assertThatThrownBy(() -> m.insertRepository(r))
                    .isInstanceOf(PersistenceException.class);
            m.suppressProject(91L, project.getId());
            assertThat(m.repository(91L, r.getId()).isAutoImportSuppressed()).isTrue();
            m.detachConnection(91L, c.getId());
            assertThat(m.repository(91L, r.getId()).getConnectionId()).isNull();
            m.cancel(91L, c.getId());
            assertThat(m.run(91L, c.getId(), run.getId()).getStatus()).isEqualTo("CANCELLED");
            m.deleteConnection(91L, c.getId());
            assertThat(m.connection(91L, c.getId())).isNull();
            assertThat(p.findById(91L, project.getId()).getName()).isEqualTo("本地项目");
            session.rollback();
        }
        try (Connection db = DriverManager.getConnection(url, "root", "");
                Statement s = db.createStatement()) {
            s.executeUpdate(
                    "DELETE FROM workspaces WHERE id IN (91,92) AND"
                        + " created_by_subject='integration-test'");
            s.executeUpdate("DELETE FROM security_principals WHERE subject_id='integration-test'");
        }
    }
}
