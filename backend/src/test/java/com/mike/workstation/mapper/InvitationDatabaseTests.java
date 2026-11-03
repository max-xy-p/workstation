package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.dto.InvitationRequest;
import com.mike.workstation.dto.InvitationResponse;
import com.mike.workstation.dto.InvitationTokenRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.AuditService;
import com.mike.workstation.service.InvitationService;
import com.mike.workstation.service.InvitationTokenService;
import com.mike.workstation.service.WorkspaceAuthorizationService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 仅连接显式指定的隔离库，验证真实锁、唯一约束和服务事务代理。 */
@EnabledIfSystemProperty(named = "workstation.test.db", matches = ".+")
class InvitationDatabaseTests {
    private JdbcTemplate jdbc;
    private InvitationMapper mapper;
    private InvitationService service;
    private InvitationTokenService tokens;
    private final AuditService audit = mock(AuditService.class);
    private final CurrentPrincipalResolver principals = mock(CurrentPrincipalResolver.class);
    private final ThreadLocal<String> actor = new ThreadLocal<>();
    private String owner;
    private String guest;
    private Long workspaceId;

    @BeforeEach
    void setup() throws Exception {
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                System.getProperty("workstation.test.db"), "root", "");
        jdbc = new JdbcTemplate(ds);
        try (var db = ds.getConnection()) {
            var script = new FileSystemResource("src/main/resources/db/migrate-workspace-invitations.sql");
            ScriptUtils.executeSqlScript(db, script);
            ScriptUtils.executeSqlScript(db, script);
        }
        var cfg = new Configuration(new Environment("invitations", new SpringManagedTransactionFactory(), ds));
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(InvitationMapper.class);
        cfg.addMapper(WorkspaceMapper.class);
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg));
        mapper = session.getMapper(InvitationMapper.class);
        var workspaces = session.getMapper(WorkspaceMapper.class);
        owner = "invite-owner-" + UUID.randomUUID();
        guest = "invite-guest-" + UUID.randomUUID();
        jdbc.update("INSERT INTO security_principals VALUES (?, 'USER', '邀请人', NULL, NOW(), NOW())", owner);
        jdbc.update("INSERT INTO workspaces(name,type,created_by_subject,created_at,updated_at) VALUES ('邀请测试','SHARED',?,NOW(),NOW())", owner);
        workspaceId = jdbc.queryForObject("SELECT id FROM workspaces WHERE created_by_subject=?", Long.class, owner);
        jdbc.update("INSERT INTO workspace_members VALUES (?,?,'OWNER',NOW())", workspaceId, owner);
        actor.set(owner);
        when(principals.require()).thenAnswer(inv -> new CurrentPrincipal(actor.get(), PrincipalType.USER, "用户", null, Set.of()));
        when(principals.requireVerifiedEmail()).thenReturn("guest@example.com");
        var properties = new InvitationProperties(true, "https://work.example",
                Base64.getEncoder().encodeToString(new byte[32]), "http://mail:8082", "x".repeat(32));
        tokens = new InvitationTokenService(properties);
        var target = new InvitationService(mapper, workspaces,
                new WorkspaceAuthorizationService(workspaces, principals, audit), principals, tokens, properties, audit);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (InvitationService) proxy.getProxy();
    }

    @AfterEach
    void cleanup() {
        if (workspaceId != null) {
            jdbc.update("DELETE FROM workspaces WHERE id=?", workspaceId);
            jdbc.update("DELETE FROM security_principals WHERE subject_id IN (?,?)", owner, guest);
        }
        actor.remove();
    }

    @Test
    void concurrentCreationUsesOneInvitationAndOneOutbox() throws Exception {
        String request = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<InvitationResponse> action = () -> {
                actor.set(owner);
                start.await();
                try {
                    return service.create(workspaceId, new InvitationRequest(request, "guest@example.com", WorkspaceRole.EDITOR));
                } finally {
                    actor.remove();
                }
            };
            var a = pool.submit(action);
            var b = pool.submit(action);
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).id()).isEqualTo(b.get(10, TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_invitations WHERE workspace_id=?", Integer.class, workspaceId)).isEqualTo(1);
            assertThat(service.list(workspaceId)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentAcceptCreatesOneMembershipAndReplayCannotRestoreRemovedAccess() throws Exception {
        var invitation = create();
        var token = new InvitationTokenRequest(tokens.token(invitation.id()));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Long> accept = () -> {
                actor.set(guest);
                start.await();
                try { return service.accept(token).id(); }
                finally { actor.remove(); }
            };
            var a = pool.submit(accept);
            var b = pool.submit(accept);
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(workspaceId);
            assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(workspaceId);
        } finally {
            pool.shutdownNow();
        }
        assertThat(mapper.find(invitation.id()).getStatus()).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND principal_subject=?", Integer.class, workspaceId, guest)).isEqualTo(1);
        jdbc.update("DELETE FROM workspace_members WHERE workspace_id=? AND principal_subject=?", workspaceId, guest);
        actor.set(guest);
        assertThatThrownBy(() -> service.accept(token)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND principal_subject=?", Integer.class, workspaceId, guest)).isZero();
    }

    @Test
    void auditFailureRollsBackInvitationAndOutboxThenMembershipAndConsumption() {
        doThrow(new IllegalStateException("模拟审计写入失败")).when(audit).success(eq(workspaceId), eq("INVITATION_CREATE"), anyString(), isNull());
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_invitations WHERE workspace_id=?", Integer.class, workspaceId)).isZero();
        reset(audit);
        var invitation = create();
        doThrow(new IllegalStateException("模拟审计写入失败")).when(audit).success(eq(workspaceId), eq("INVITATION_ACCEPT"), anyString(), isNull());
        actor.set(guest);
        assertThatThrownBy(() -> service.accept(new InvitationTokenRequest(tokens.token(invitation.id())))).isInstanceOf(IllegalStateException.class);
        assertThat(mapper.find(invitation.id()).getStatus()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND principal_subject=?", Integer.class, workspaceId, guest)).isZero();
    }

    @Test
    void revokedInvitationAndStalePublisherCannotUndoConsumerCompletion() {
        var invitation = create();
        var now = LocalDateTime.now(ZoneId.systemDefault());
        String deliveryId = mapper.find(invitation.id()).getDeliveryId();
        assertThat(mapper.due(now.plusSeconds(1))).contains(deliveryId);
        assertThat(mapper.claim(deliveryId, "first", now.plusSeconds(1), now.plusSeconds(30))).isEqualTo(1);
        assertThat(mapper.claim(deliveryId, "second", now.plusSeconds(1), now.plusSeconds(30))).isZero();
        assertThat(mapper.claim(deliveryId, "recovered", now.plusSeconds(31), now.plusSeconds(61))).isEqualTo(1);
        assertThat(mapper.published(deliveryId, "first", "PUBLISHED", now.plusSeconds(60))).isZero();
        mapper.completed(deliveryId, "ACCEPTED");
        assertThat(mapper.published(deliveryId, "recovered", "PUBLISHED", now.plusSeconds(90))).isZero();
        service.revoke(workspaceId, invitation.id());
        assertThat(mapper.find(invitation.id()).getStatus()).isEqualTo("REVOKED");
        assertThat(mapper.find(invitation.id()).getMailStatus()).isEqualTo("ACCEPTED");
        actor.set(guest);
        assertThatThrownBy(() -> service.accept(new InvitationTokenRequest(tokens.token(invitation.id())))).isInstanceOf(BusinessException.class);
    }

    private InvitationResponse create() {
        return service.create(workspaceId, new InvitationRequest(UUID.randomUUID().toString(),
                "guest@example.com", WorkspaceRole.EDITOR));
    }
}
