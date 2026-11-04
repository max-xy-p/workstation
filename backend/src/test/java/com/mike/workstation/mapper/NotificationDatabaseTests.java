package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.domain.NotificationResourceType;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.dto.NotificationReadAllRequest;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.NotificationCodec;
import com.mike.workstation.service.NotificationCommand;
import com.mike.workstation.service.NotificationWriter;
import com.mike.workstation.service.NotificationService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 通知直接参加业务事务；待发布标记只负责提交后的 MQ 刷新广播。 */
@EnabledIfSystemProperty(named = "workstation.test.db", matches = ".+")
class NotificationDatabaseTests {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private DataSourceTransactionManager transactions;
    private NotificationMapper mapper;
    private NotificationEventMapper events;
    private NotificationSignalMapper signals;
    private NotificationWriter writer;
    private NotificationService service;
    private final CurrentPrincipalResolver principal = mock(CurrentPrincipalResolver.class);
    private String owner;
    private String recipient;
    private String outsider;
    private String machine;
    private Long workspaceId;
    private Long otherWorkspaceId;

    @BeforeEach
    void setup() throws Exception {
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getProperty("workstation.test.db"), "root", "");
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(transactions);
        try (var db = ds.getConnection()) {
            var sql = new FileSystemResource("src/main/resources/db/migrate-notifications.sql");
            ScriptUtils.executeSqlScript(db, sql);
            ScriptUtils.executeSqlScript(db, sql);
        }
        var cfg = new Configuration(new Environment("notification-tests", new SpringManagedTransactionFactory(), ds));
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(NotificationMapper.class);
        cfg.addMapper(NotificationEventMapper.class);
        cfg.addMapper(NotificationSignalMapper.class);
        cfg.addMapper(WorkspaceMapper.class);
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg));
        mapper = session.getMapper(NotificationMapper.class);
        events = session.getMapper(NotificationEventMapper.class);
        signals = session.getMapper(NotificationSignalMapper.class);
        String suffix = UUID.randomUUID().toString();
        owner = "notify-owner-" + suffix;
        recipient = "notify-user-" + suffix;
        outsider = "notify-other-" + suffix;
        machine = "notify-machine-" + suffix;
        for (String subject : List.of(owner, recipient, outsider, machine)) {
            jdbc.update("INSERT INTO security_principals VALUES (?, ?, '通知测试', NULL, NOW(), NOW())",
                    subject, subject.equals(machine) ? "SERVICE" : "USER");
        }
        workspaceId = createWorkspace("通知测试空间");
        otherWorkspaceId = createWorkspace("另一空间");
        for (Long workspace : List.of(workspaceId, otherWorkspaceId)) {
            jdbc.update("INSERT INTO workspace_members VALUES (?,?,'OWNER',NOW()),(?,?,'VIEWER',NOW()),(?,?,'VIEWER',NOW())",
                    workspace, owner, workspace, recipient, workspace, machine);
        }
        var properties = new NotificationProperties(true, 500, 5, 90);
        writer = proxy(new NotificationWriter(events, mapper, session.getMapper(WorkspaceMapper.class),
                new NotificationCodec(new ObjectMapper()), properties), NotificationWriter.class);
        when(principal.require()).thenReturn(new CurrentPrincipal(recipient, PrincipalType.USER, "用户", null, Set.of()));
        service = proxy(new NotificationService(mapper, principal, properties), NotificationService.class);
    }

    @AfterEach
    void cleanup() {
        if (workspaceId != null) {
            jdbc.update("DELETE FROM workspaces WHERE id IN (?,?)", workspaceId, otherWorkspaceId);
            jdbc.update("DELETE FROM security_principals WHERE subject_id IN (?,?,?,?)", owner, recipient, outsider, machine);
        }
    }

    private <T> T proxy(T target, Class<T> type) {
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }

    @Test
    void writerSavesImmediatelyAndRollsBackTogetherWithBusinessAndSignalMarker() {
        var command = command(workspaceId, List.of(recipient));
        assertThatThrownBy(() -> writer.writeRequired(command)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE workspaces SET name='不应提交' WHERE id=?", workspaceId);
            writer.writeRequired(command);
            assertThat(service.state().unreadCount()).isEqualTo(1);
            assertThat(service.state().latestSequence()).isEqualTo(1);
            throw new IllegalStateException("模拟业务失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.state().latestSequence()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_events WHERE event_id=?", Integer.class, command.eventId())).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM workspaces WHERE id=?", String.class, workspaceId)).isEqualTo("通知测试空间");
        store(command);
        assertThat(service.state().unreadCount()).isEqualTo(1);
        assertThat(signals.due(LocalDateTime.now(ZoneId.systemDefault()).plusSeconds(1))).contains(recipient);
    }

    @Test
    void duplicateEventIsSynchronousAndOnlyHumanMembersReceiveIt() {
        var command = command(workspaceId, List.of(recipient, machine, outsider));
        store(command);
        store(command);
        assertThat(service.state().latestSequence()).isEqualTo(1);
        assertThat(service.list(0, 20, false, null).items()).hasSize(1);
        assertThat(mapper.state(machine).latestSequence()).isZero();
        assertThat(mapper.state(outsider).latestSequence()).isZero();
        var changed = new NotificationCommand(command.eventId(), workspaceId, command.type(), "不同正文",
                command.body(), command.resourceType(), command.resourceId(), command.occurredAt(), command.recipients());
        assertThatThrownBy(() -> store(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.state().latestSequence()).isEqualTo(1);
    }

    @Test
    void readAllBoundaryKeepsNewArrivalUnreadAndOtherUsersCannotReadIt() {
        store(command(workspaceId, List.of(recipient)));
        long seen = service.state().latestSequence();
        store(command(workspaceId, List.of(recipient)));
        service.readAll(new NotificationReadAllRequest(seen));
        assertThat(service.state().unreadCount()).isEqualTo(1);
        var unread = service.list(0, 20, true, null).items();
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).sequence()).isEqualTo(2);
        service.read(unread.get(0).id());
        service.read(unread.get(0).id());
        assertThat(service.state().unreadCount()).isZero();
        when(principal.require()).thenReturn(new CurrentPrincipal(outsider, PrincipalType.USER, "其他人", null, Set.of()));
        assertThatThrownBy(() -> service.get(unread.get(0).id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.read(unread.get(0).id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void membershipRemovalHidesHistoryAndOldEventCannotAddRecipientsOnReplay() {
        store(command(workspaceId, List.of(recipient)));
        String id = service.list(0, 20, false, null).items().get(0).id();
        jdbc.update("DELETE FROM workspace_members WHERE workspace_id=? AND principal_subject=?", workspaceId, recipient);
        assertThat(service.state().unreadCount()).isZero();
        assertThat(service.list(0, 20, false, null).items()).isEmpty();
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
        var skipped = command(workspaceId, List.of(recipient));
        store(skipped);
        jdbc.update("INSERT INTO workspace_members VALUES (?,?,'VIEWER',NOW())", workspaceId, recipient);
        store(skipped);
        assertThat(service.state().latestSequence()).isEqualTo(1);
    }

    @Test
    void currentMembershipOverridesOldRepeatableReadSnapshot() throws Exception {
        var command = command(workspaceId, List.of(recipient));
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT name FROM workspaces WHERE id=?", String.class, workspaceId);
                try {
                    executor.submit(() -> jdbc.update("DELETE FROM workspace_members WHERE workspace_id=? AND principal_subject=?", workspaceId, recipient)).get(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("隔离测试删除成员失败", exception);
                }
                writer.writeRequired(command);
            });
        } finally {
            executor.shutdownNow();
        }
        assertThat(service.state().latestSequence()).isZero();
    }

    @Test
    void concurrentWritesAcrossSpacesHaveCommittedPerUserSequenceAndStablePagination() throws Exception {
        var a = command(workspaceId, List.of(recipient));
        var b = command(otherWorkspaceId, List.of(recipient));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { await(start); store(a); });
            var second = pool.submit(() -> { await(start); store(b); });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(service.state().latestSequence()).isEqualTo(2);
        assertThat(service.list(0, 1, false, 2L).items().get(0).sequence()).isEqualTo(2);
        store(command(workspaceId, List.of(recipient)));
        assertThat(service.list(1, 1, false, 2L).items().get(0).sequence()).isEqualTo(1);
    }

    @Test
    void confirmCannotClearNewerChangesAndLeaseCanRecoverAfterCrash() {
        store(command(workspaceId, List.of(recipient)));
        var now = LocalDateTime.now(ZoneId.systemDefault()).plusSeconds(1);
        assertThat(signals.claim(recipient, "old", now, now.plusSeconds(30))).isEqualTo(1);
        long revision = signals.snapshot(recipient).revision();
        store(command(workspaceId, List.of(recipient)));
        signals.published(recipient, "old", revision, now);
        assertThat(signals.due(now)).contains(recipient);
        assertThat(signals.claim(recipient, "second", now, now.plusSeconds(30))).isEqualTo(1);
        assertThat(signals.claim(recipient, "third", now, now.plusSeconds(30))).isZero();
        assertThat(signals.claim(recipient, "recovered", now.plusSeconds(31), now.plusSeconds(61))).isEqualTo(1);
        assertThat(signals.published(recipient, "second", 2, now)).isZero();
        assertThat(signals.published(recipient, "recovered", signals.snapshot(recipient).revision(), now)).isEqualTo(1);
        assertThat(signals.due(now)).doesNotContain(recipient);
        service.readAll(new NotificationReadAllRequest(2L));
        assertThat(signals.due(now.plusSeconds(1))).contains(recipient);
    }

    @Test
    void retentionDoesNotResetSequence() {
        var command = command(workspaceId, List.of(recipient));
        store(command);
        jdbc.update("UPDATE notifications SET created_at=DATE_SUB(NOW(), INTERVAL 100 DAY) WHERE event_id=?", command.eventId());
        jdbc.update("UPDATE notification_events SET created_at=DATE_SUB(NOW(), INTERVAL 100 DAY) WHERE event_id=?", command.eventId());
        var cutoff = LocalDateTime.now(ZoneId.systemDefault()).minusDays(90);
        mapper.purge(cutoff);
        events.purge(cutoff);
        assertThat(service.list(0, 20, false, null).items()).isEmpty();
        store(command(workspaceId, List.of(recipient)));
        assertThat(service.state().latestSequence()).isEqualTo(2);
    }

    private void store(NotificationCommand command) {
        tx.executeWithoutResult(status -> writer.writeRequired(command));
    }

    private NotificationCommand command(Long workspace, List<String> recipients) {
        return new NotificationCommand(UUID.randomUUID().toString(), workspace, "TEST_EVENT", "测试通知",
                "纯文本通知正文", NotificationResourceType.WORKSPACE, null, Instant.now(), recipients);
    }

    private Long createWorkspace(String name) {
        jdbc.update("INSERT INTO workspaces(name,type,created_by_subject,created_at,updated_at) VALUES (?,'SHARED',?,NOW(),NOW())", name, owner);
        return jdbc.queryForObject("SELECT id FROM workspaces WHERE created_by_subject=? AND name=?", Long.class, owner, name);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("测试已中断"); }
    }
}
