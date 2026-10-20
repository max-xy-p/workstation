package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.security.*;
import com.mike.workstation.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 显式隔离库中的真实唯一约束、条件关联、事务回滚和持久化跟踪验证。 */
@EnabledIfSystemProperty(named="workstation.test.db",matches=".+")
class TaskIssueDatabaseTests {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private IssueSourceMapper sources;
    private IntegrationMapper integrations;
    private TaskMapper taskMapper;
    private TaskService tasks;
    private IntegrationStateService leases;
    private TaskIssueService service;
    private final AuditService audit=mock(AuditService.class);
    private final WorkspaceAuthorizationService auth=mock(WorkspaceAuthorizationService.class);
    private final CurrentPrincipalResolver principal=mock(CurrentPrincipalResolver.class);
    private String subject;
    private Long w,project,repo,candidate,connection;
    @BeforeEach void setup() {
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",System.getProperty("workstation.test.db"),"root","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var cfg=new Configuration(new Environment("issues",new SpringManagedTransactionFactory(),ds));cfg.setMapUnderscoreToCamelCase(true);
        for(Class<?> m:List.of(IssueSourceMapper.class,IntegrationMapper.class,TaskMapper.class,ProjectMapper.class,NoteMapper.class,WorkspaceMapper.class)) cfg.addMapper(m);
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg));sources=session.getMapper(IssueSourceMapper.class);integrations=session.getMapper(IntegrationMapper.class);taskMapper=session.getMapper(TaskMapper.class);
        subject="issue-"+UUID.randomUUID();jdbc.update("INSERT INTO security_principals VALUES (?,'USER','测试',NULL,NOW(),NOW())",subject);
        jdbc.update("INSERT INTO workspaces(name,type,created_by_subject,created_at,updated_at) VALUES (?,'SHARED',?,NOW(),NOW())",subject,subject);
        w=jdbc.queryForObject("SELECT id FROM workspaces WHERE created_by_subject=?",Long.class,subject);
        jdbc.update("INSERT INTO workspace_members(workspace_id,principal_subject,role,joined_at) VALUES (?,?,'OWNER',NOW())",w,subject);
        Workspace space=new Workspace();space.setType(WorkspaceType.SHARED);
        when(auth.require(eq(w),any())).thenAnswer(inv->{jdbc.queryForObject("SELECT name FROM workspaces WHERE id=?",String.class,w);return space;});
        when(principal.require()).thenReturn(new CurrentPrincipal(subject,PrincipalType.USER,"测试",null,Set.of()));
        jdbc.update("INSERT INTO projects(workspace_id,name,status,created_at,updated_at) VALUES (?,'项目','ACTIVE',NOW(),NOW())",w);
        project=jdbc.queryForObject("SELECT id FROM projects WHERE workspace_id=?",Long.class,w);
        IntegrationConnection c=new IntegrationConnection();c.setWorkspaceId(w);c.setName("测试连接");c.setProvider("GITHUB");c.setBaseUrl("https://github.com");c.setAccount("测试");c.setCredential("encrypted");c.setKeyVersion("v1");c.setScope("OWNED");c.setOwnerSubject(subject);c.setEnabled(true);c.setVersion(1);integrations.insertConnection(c);connection=c.getId();
        ExternalRepository r=new ExternalRepository();r.setWorkspaceId(w);r.setConnectionId(connection);r.setProvider("GITHUB");r.setBaseUrl("https://github.com");r.setExternalId("1000");r.setName("repo");r.setNamespace("test");r.setVisibility("private");r.setUrl("https://github.com/test/repo");integrations.insertRepository(r);repo=r.getId();integrations.bind(w,project,repo);
        DevelopmentItem d=new DevelopmentItem();d.setWorkspaceId(w);d.setRepositoryId(repo);d.setKind("ISSUE");d.setExternalId("100");d.setTitle("原始 Issue");d.setStatus("OPEN");d.setIssueNumber(1L);integrations.insertDevelopment(d);
        candidate=jdbc.queryForObject("SELECT id FROM external_development_items WHERE workspace_id=?",Long.class,w);
        tasks=new TaskService(taskMapper,session.getMapper(ProjectMapper.class),session.getMapper(NoteMapper.class),auth,audit,sources);
        service=new TaskIssueService(sources,integrations,tasks,auth,principal,audit);
        leases=new IntegrationStateService(integrations,session.getMapper(ProjectMapper.class),session.getMapper(WorkspaceMapper.class),
                auth,principal,mock(com.mike.workstation.integration.CredentialCipher.class),
                new com.mike.workstation.config.IntegrationProperties(true,"v1",Map.of(),List.of(),15,10,100,180,30),audit,sources);
    }
    @AfterEach void cleanup() {
        if(w==null)return;
        for(String table:List.of("tasks","external_issue_sources","integration_sync_items","integration_sync_runs","external_development_items","external_development_snapshots","project_repositories","external_repositories","integration_connections","projects","workspace_members")) jdbc.update("DELETE FROM "+table+" WHERE workspace_id=?",w);
        jdbc.update("DELETE FROM workspaces WHERE id=?",w);jdbc.update("DELETE FROM security_principals WHERE subject_id=?",subject);
    }
    private TaskRequest request() { return new TaskRequest("本地执行",null,project,TaskStatus.TODO,TaskPriority.HIGH,null,null); }
    private IssueTaskResult create() { return tx.execute(status->service.create(w,project,new IssueTaskCreateRequest(candidate,true,request()))); }
    @Test void concurrentCreateHasOneTaskEvenWithEarlierReadSnapshot() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            Callable<IssueTaskResult> action=()->{ready.countDown();assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return create();};
            var a=pool.submit(action);var b=pool.submit(action);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            IssueTaskResult first=a.get(20,TimeUnit.SECONDS),second=b.get(20,TimeUnit.SECONDS);
            assertThat(first.task().id()).isEqualTo(second.task().id());assertThat(first.created()).isNotEqualTo(second.created());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE workspace_id=?",Long.class,w)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void snapshotReplacementDoesNotRemoveSourceAndOldTaskUpdatesKeepLink() {
        var created=create();integrations.clearDevelopment(w,repo,"ISSUE");
        assertThat(service.list(w,project,0,20).items()).hasSize(1);
        tx.executeWithoutResult(status->tasks.update(w,created.task().id(),request()));
        assertThat(sources.forTask(w,created.task().id())).isNotNull();
        assertThat(integrations.development(w,project,"ISSUE",0,20)).isEmpty();
        assertThat(sources.pending(w,connection,0,sources.upperBound(w))).hasSize(1);
    }
    @Test void auditFailureRollsBackTaskSourceAndLink() {
        doThrow(new IllegalStateException("审计失败")).when(audit).success(eq(w),eq("TASK_ISSUE_LINK"),anyString(),anyLong());
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE workspace_id=?",Long.class,w)).isZero();
        assertThat(sources.upperBound(w)).isZero();
    }
    @Test void sourceUniquenessCrossWorkspaceAndDeleteCascade() {
        var result=create();IssueSource s=sources.forTask(w,result.task().id());
        assertThat(sources.list(w+999,null,0,20)).isEmpty();
        assertThatThrownBy(()->sources.link(w,result.task().id(),s.getId(),subject)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        tx.executeWithoutResult(status->tasks.delete(w,result.task().id()));assertThat(sources.count(w,null)).isZero();
        assertThat(sources.upperBound(w)).isGreaterThan(0);assertThat(create().created()).isTrue();
    }
    @Test void unbindStopsTrackingAndReattachChangesGeneration() {
        var result=create();IssueSource original=sources.forTask(w,result.task().id());
        tx.executeWithoutResult(status->service.unlink(w,result.task().id()));
        create();IssueSource replacement=sources.forTask(w,jdbc.queryForObject("SELECT task_id FROM task_issue_links WHERE workspace_id=?",Long.class,w));
        assertThat(replacement.getLinkToken()).isNotEqualTo(original.getLinkToken());
        integrations.unbind(w,project,repo);assertThat(sources.pending(w,connection,0,sources.upperBound(w))).isEmpty();
        assertThat(sources.list(w,null,0,20)).singleElement().satisfies(s->assertThat(s.getTrackingStatus()).isEqualTo("STOPPED"));
    }

    @Test void trackingCommitsSnapshotAndResumeCursorButNeverLocalTaskFields() {
        var created=create();IssueSource selected=sources.forTask(w,created.task().id());
        var run=new IntegrationRun();run.setWorkspaceId(w);run.setConnectionId(connection);run.setKind("SYNC");run.setStatus("QUEUED");
        run.setActorSubject(subject);run.setConfigVersion(1);run.setRequestKey("tracking");run.setRequestHash("hash");run.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
        integrations.insertRun(run);run.setLeaseToken("tracking-lease");run.setStatus("RUNNING");run.setLeaseUntil(run.getCreatedAt().plusMinutes(3));integrations.claim(run);
        var tracking=new IssueTrackingStateService(sources,integrations,leases);tx.executeWithoutResult(status->tracking.begin(run));
        DevelopmentItem fresh=new DevelopmentItem();fresh.setExternalId("100");fresh.setIssueNumber(1L);fresh.setTitle("来源已关闭");fresh.setStatus("CLOSED");fresh.setStateReason("completed");fresh.setSourceUpdatedAt(run.getCreatedAt());
        tx.executeWithoutResult(status->tracking.complete(run,selected,fresh,null));
        IssueSource saved=sources.forTask(w,created.task().id());assertThat(saved.getStatus()).isEqualTo("CLOSED");assertThat(saved.getLastSuccessAt()).isNotNull();
        assertThat(integrations.run(w,connection,run.getId()).getIssueCursor()).isEqualTo(selected.getId());
        assertThat(tasks.get(w,created.task().id()).title()).isEqualTo("本地执行");assertThat(tasks.get(w,created.task().id()).status()).isEqualTo(TaskStatus.TODO);
        tx.executeWithoutResult(status->tracking.complete(run,selected,null,"平台资源不可读取"));
        assertThat(sources.forTask(w,created.task().id()).getStatus()).isEqualTo("CLOSED");
        assertThat(sources.forTask(w,created.task().id()).getLastError()).isEqualTo("平台资源不可读取");
        jdbc.update("DELETE FROM integration_sync_runs WHERE workspace_id=?",w);
    }
    @Test void keysetBatchesReachTailAndDoNotChangeRecentSnapshotCounts() {
        create();
        tx.executeWithoutResult(status->{
            for(int index=0;index<54;index++) {
                jdbc.update("INSERT INTO external_issue_sources(workspace_id,repository_id,provider,base_url,external_id,issue_number,title,status) VALUES (?,?,'GITHUB','https://github.com',?,?,?,'OPEN')",w,repo,"extra-"+index,index+10,"历史来源");
                Long sourceId=jdbc.queryForObject("SELECT MAX(id) FROM external_issue_sources WHERE workspace_id=?",Long.class,w);
                TaskResponse task=tasks.create(w,request());sources.link(w,task.id(),sourceId,subject);
            }
            long upper=sources.upperBound(w);var first=sources.pending(w,connection,0,upper);assertThat(first).hasSize(50);
            var tail=sources.pending(w,connection,first.get(49).getId(),upper);assertThat(tail).hasSize(5);
            assertThat(integrations.countDevelopment(w,project,"ISSUE")).isEqualTo(1);
            status.setRollbackOnly();
        });
    }
}
