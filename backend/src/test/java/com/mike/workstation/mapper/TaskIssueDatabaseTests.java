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
    private GithubWebhookMapper hooks;
    private GithubWebhookService webhook;
    private GithubWebhookDispatcher dispatcher;
    private GithubWebhookResponse hook;
    private final String webhookSecret="webhook-test-secret-with-more-than-32-characters";
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
        for(Class<?> m:List.of(IssueSourceMapper.class,IntegrationMapper.class,TaskMapper.class,ProjectMapper.class,NoteMapper.class,WorkspaceMapper.class,GithubWebhookMapper.class)) cfg.addMapper(m);
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg));sources=session.getMapper(IssueSourceMapper.class);integrations=session.getMapper(IntegrationMapper.class);taskMapper=session.getMapper(TaskMapper.class);hooks=session.getMapper(GithubWebhookMapper.class);
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
                new com.mike.workstation.config.IntegrationProperties(true,"v1",Map.of(),List.of(),15,10,100,180,30),audit,sources,session.getMapper(GithubWebhookMapper.class));
    }
    @AfterEach void cleanup() {
        if(w==null)return;
        for(String table:List.of("github_webhook_deliveries","integration_issue_targets","integration_webhooks","tasks","external_issue_sources","integration_sync_items","integration_sync_runs","external_development_items","external_development_snapshots","project_repositories","external_repositories","integration_connections","projects","workspace_members")) jdbc.update("DELETE FROM "+table+" WHERE workspace_id=?",w);
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
        var tracking=new IssueTrackingStateService(sources,integrations,leases,mock(GithubWebhookMapper.class));tx.executeWithoutResult(status->tracking.begin(run));
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

    private void prepareWebhook() {
        create();
        var props=new com.mike.workstation.config.IntegrationProperties(true,"v1",Map.of("v1",Base64.getEncoder().encodeToString(new byte[32])),List.of(),15,10,100,180,30);
        webhook=new GithubWebhookService(hooks,integrations,sources,leases,auth,new com.mike.workstation.integration.CredentialCipher(props),props,audit,new tools.jackson.databind.ObjectMapper());
        dispatcher=new GithubWebhookDispatcher(hooks,integrations,sources,leases);
        hook=tx.execute(status->webhook.configure(w,connection,new GithubWebhookRequest(true,0,webhookSecret)));
    }
    private void receive(String delivery) {
        String endpoint=hook.endpointPath().substring(hook.endpointPath().lastIndexOf('/')+1);
        byte[] body="{\"action\":\"closed\",\"repository\":{\"id\":1000},\"issue\":{\"id\":100,\"number\":1,\"title\":\"中文\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            String signature="sha256="+HexFormat.of().formatHex(mac.doFinal(body));
            tx.executeWithoutResult(status->webhook.receive(endpoint,delivery,"issues",signature,body));
        } catch(java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    private IntegrationRun dispatchAndClaim() {
        tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));
        IntegrationRun claimed=tx.execute(status->leases.claim());
        assertThat(claimed).as("投递时间与状态：%s",jdbc.queryForList("SELECT status,next_attempt_at,NOW() AS db_now FROM github_webhook_deliveries WHERE workspace_id=?",w)).isNotNull();
        return claimed;
    }
    private void completeTarget(IntegrationRun run,String error) {
        var selected=hooks.targets(w,run.getId()).get(0);
        var fresh=new DevelopmentItem();fresh.setExternalId("100");fresh.setIssueNumber(1L);fresh.setTitle("最新来源");fresh.setStatus("CLOSED");fresh.setSourceUpdatedAt(java.time.LocalDateTime.of(2026,10,20,12,0));
        var tracking=new IssueTrackingStateService(sources,integrations,leases,hooks);
        tx.executeWithoutResult(status->tracking.completeTarget(run,selected,fresh,error));
        tx.executeWithoutResult(status->leases.finish(run,error==null?"SUCCEEDED":"PARTIAL",error));
    }
    @Test void webhookDuplicateAndBurstCoalesceButNewNotificationIsNotLost() {
        prepareWebhook();receive("first");receive("first");receive("second");
        assertThat(hooks.count(w,connection)).isEqualTo(2);
        var run=dispatchAndClaim();assertThat(run.getKind()).isEqualTo("ISSUE_REFRESH");assertThat(hooks.targets(w,run.getId())).hasSize(1);
        receive("during-running");tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));
        assertThat(hooks.list(w,connection,0,20)).filteredOn(d->"RECEIVED".equals(d.getStatus())).hasSize(1);
        completeTarget(run,null);
        tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));
        assertThat(hooks.list(w,connection,0,20)).filteredOn(d->"SUCCEEDED".equals(d.getStatus())).hasSize(2);
        var next=integrations.active(w,connection);assertThat(next.getId()).isNotEqualTo(run.getId());assertThat(hooks.targets(w,next.getId())).hasSize(1);
        assertThat(integrations.connection(w,connection).getLastSyncedAt()).isNull();
    }
    @Test void webhookConcurrentDuplicateDeliveryCommitsOneInboxRow() throws Exception {
        prepareWebhook();var pool=Executors.newFixedThreadPool(2);
        try { var a=pool.submit(()->receive("same"));var b=pool.submit(()->receive("same"));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);assertThat(hooks.count(w,connection)).isEqualTo(1); }
        finally { pool.shutdownNow(); }
    }
    @Test void webhookLeaseRecoveryReclaimsJobAndRejectsOldExecutor() {
        prepareWebhook();receive("recover");var old=dispatchAndClaim();
        jdbc.update("UPDATE integration_sync_runs SET lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",old.getId());
        var resumed=tx.execute(status->leases.claim());assertThat(resumed.getId()).isEqualTo(old.getId());assertThat(resumed.getLeaseToken()).isNotEqualTo(old.getLeaseToken());
        assertThat(hooks.targets(w,resumed.getId())).hasSize(1);
        assertThatThrownBy(()->tx.executeWithoutResult(status->leases.heartbeat(old))).isInstanceOf(com.mike.workstation.exception.IntegrationConflictException.class);
        completeTarget(resumed,null);assertThat(hooks.targets(w,resumed.getId())).isEmpty();
        tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));assertThat(hooks.list(w,connection,0,20)).singleElement().satisfies(d->assertThat(d.getStatus()).isEqualTo("SUCCEEDED"));
    }
    @Test void webhookFailureBackoffExhaustionAndExplicitRetry() {
        prepareWebhook();receive("failure");
        for(int attempt=1;attempt<=3;attempt++) {
            var run=dispatchAndClaim();completeTarget(run,"平台暂时不可用");tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));
            var delivery=hooks.list(w,connection,0,20).get(0);assertThat(delivery.getAttempts()).isEqualTo(attempt);
            assertThat(delivery.getStatus()).isEqualTo(attempt<3?"RETRY":"FAILED");
            assertThat(integrations.active(w,connection)).isNull();
            jdbc.update("UPDATE github_webhook_deliveries SET next_attempt_at=NOW() WHERE workspace_id=?",w);
        }
        var failed=hooks.list(w,connection,0,20).get(0);tx.executeWithoutResult(status->webhook.retry(w,connection,failed.getId()));
        assertThat(hooks.list(w,connection,0,20).get(0).getAttempts()).isZero();assertThat(dispatchAndClaim()).isNotNull();
    }
    @Test void webhookRotationCancelsOldExecutionAndCannotRestoreIt() {
        prepareWebhook();receive("rotate");var old=dispatchAndClaim();
        tx.executeWithoutResult(status->webhook.configure(w,connection,new GithubWebhookRequest(false,1,null)));
        assertThat(integrations.run(w,connection,old.getId()).getStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(()->tx.executeWithoutResult(status->leases.heartbeat(old))).isInstanceOf(com.mike.workstation.exception.IntegrationConflictException.class);
        assertThat(hooks.list(w,connection,0,20).get(0).getStatus()).isEqualTo("CANCELLED");
    }
    @Test void webhookOldGenerationIsIgnoredAfterUnlinkAndRelink() {
        prepareWebhook();receive("old-generation");
        Long task=sources.list(w,project,0,20).get(0).getTaskId();tx.executeWithoutResult(status->service.unlink(w,task));create();
        tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));assertThat(integrations.active(w,connection)).isNull();
        assertThat(hooks.list(w,connection,0,20).get(0).getStatus()).isEqualTo("IGNORED");
    }
    @Test void webhookDuePollingGetsPriorityOverTargetedNotifications() {
        prepareWebhook();receive("poll-first");jdbc.update("UPDATE integration_connections SET schedule_minutes=1,next_sync_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",connection);
        tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));assertThat(integrations.active(w,connection)).isNull();
        tx.executeWithoutResult(status->leases.schedule(w,connection));assertThat(integrations.active(w,connection).getKind()).isEqualTo("SYNC");
    }
    @Test void webhookReceiptAndTargetProgressRollbackTogetherOnFailure() {
        prepareWebhook();receive("rollback");var run=dispatchAndClaim();
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            var selected=hooks.targets(w,run.getId()).get(0);new IssueTrackingStateService(sources,integrations,leases,hooks).completeTarget(run,selected,null,"模拟失败");
            throw new IllegalStateException("事务回滚");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(hooks.targets(w,run.getId())).hasSize(1);assertThat(sources.list(w,project,0,20).get(0).getLastAttemptAt()).isNull();
    }

    @Test void webhookClaimSurvivesAbruptJvmExit() throws Exception {
        prepareWebhook();receive("process-crash");tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));
        var queued=integrations.active(w,connection);
        var output=java.nio.file.Path.of("target","webhook-crash-"+queued.getId()+".log").toFile();
        String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
        var process=new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),ClaimAndCrash.class.getName(),
                System.getProperty("workstation.test.db"),queued.getId().toString()).redirectErrorStream(true).redirectOutput(output).start();
        try { assertThat(process.waitFor(40,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).as(java.nio.file.Files.readString(output.toPath())).isEqualTo(17); }
        finally { if(process.isAlive()) process.destroyForcibly(); }
        var abandoned=integrations.run(w,connection,queued.getId());assertThat(abandoned.getStatus()).isEqualTo("RUNNING");assertThat(hooks.targets(w,queued.getId())).hasSize(1);
        jdbc.update("UPDATE integration_sync_runs SET lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",queued.getId());
        var resumed=tx.execute(status->leases.claim());assertThat(resumed.getLeaseToken()).isNotEqualTo(abandoned.getLeaseToken());completeTarget(resumed,null);
        tx.executeWithoutResult(status->dispatcher.dispatch(w,connection));assertThat(hooks.list(w,connection,0,20).get(0).getStatus()).isEqualTo("SUCCEEDED");
    }

    private IssueSourceConfirmRequest confirmation(TaskIssueSourceResponse source,IssueSourceConfirmRequest.Action action) {
        return new IssueSourceConfirmRequest(source.linkToken(),source.sourceVersion(),source.confirmationVersion(),source.taskVersion(),action);
    }
    private void changeSource(Long task,String title,String status,String reason) {
        tx.executeWithoutResult(transaction->{
            var source=sources.forTaskLocked(w,task);source.setTitle(title);source.setStatus(status);source.setStateReason(reason);
            source.setLastAttemptAt(java.time.LocalDateTime.now());source.setLastSuccessAt(java.time.LocalDateTime.now());sources.update(source);
        });
    }
    private TaskIssueSourceResponse currentSource() { return service.list(w,project,0,20).items().get(0); }
    @Test void baselineStartsCleanAndRepeatedReadsDoNotRepeatChanges() {
        Long task=create().task().id();assertThat(currentSource().pending()).isFalse();assertThat(currentSource().confirmedAt()).isNull();
        changeSource(task,"新标题","CLOSED","completed");var changed=currentSource();assertThat(changed.pending()).isTrue();assertThat(changed.sourceVersion()).isEqualTo(1L);
        changeSource(task,"新标题","CLOSED","completed");assertThat(currentSource().sourceVersion()).isEqualTo(changed.sourceVersion());
        var result=tx.execute(transaction->service.confirm(w,task,confirmation(changed,IssueSourceConfirmRequest.Action.KEEP)));
        assertThat(result.pending()).isFalse();assertThat(result.confirmedBy()).isEqualTo(subject);assertThat(result.confirmedAt()).isNotNull();
        assertThat(tasks.get(w,task).status()).isEqualTo(TaskStatus.TODO);
        changeSource(task,"新标题","OPEN","reopened");assertThat(currentSource().pending()).isTrue();
    }
    @Test void returnedStateAndFailuresDoNotCreateOrClearBusinessDifference() {
        Long task=create().task().id();changeSource(task,"新标题","CLOSED",null);assertThat(currentSource().pending()).isTrue();
        changeSource(task,"原始 Issue","OPEN",null);assertThat(currentSource().pending()).isFalse();
        changeSource(task,"原始 issue","OPEN",null);assertThat(currentSource().pending()).isTrue();
        jdbc.update("UPDATE external_issue_sources SET last_error='模拟失败' WHERE workspace_id=?",w);
        assertThat(currentSource().pending()).isTrue();assertThat(currentSource().trackingStatus()).isEqualTo("ERROR");
    }
    @Test void staleSourceTaskConfirmationAndGenerationCannotAdvanceBaseline() {
        Long task=create().task().id();changeSource(task,"新标题","CLOSED",null);var first=currentSource();
        changeSource(task,"再变一次","CLOSED",null);
        assertThatThrownBy(()->tx.execute(t->service.confirm(w,task,confirmation(first,IssueSourceConfirmRequest.Action.KEEP)))).isInstanceOf(com.mike.workstation.exception.TaskIssueConflictException.class);
        var second=currentSource();tx.executeWithoutResult(t->tasks.updateStatus(w,task,TaskStatus.IN_PROGRESS));
        assertThatThrownBy(()->tx.execute(t->service.confirm(w,task,confirmation(second,IssueSourceConfirmRequest.Action.KEEP)))).isInstanceOf(com.mike.workstation.exception.TaskIssueConflictException.class);
        var third=currentSource();tx.execute(t->service.confirm(w,task,confirmation(third,IssueSourceConfirmRequest.Action.KEEP)));
        changeSource(task,"新变化","OPEN",null);
        assertThatThrownBy(()->tx.execute(t->service.confirm(w,task,confirmation(third,IssueSourceConfirmRequest.Action.KEEP)))).isInstanceOf(com.mike.workstation.exception.TaskIssueConflictException.class);
        var old=currentSource();tx.executeWithoutResult(t->service.unlink(w,task));
        tx.execute(t->service.link(w,project,new IssueTaskLinkRequest(candidate,true,task)));
        assertThat(currentSource().pending()).isFalse();assertThat(currentSource().confirmedBy()).isNull();
        assertThatThrownBy(()->tx.execute(t->service.confirm(w,task,confirmation(old,IssueSourceConfirmRequest.Action.KEEP)))).isInstanceOf(com.mike.workstation.exception.TaskIssueConflictException.class);
    }
    @Test void taskTransitionAndBaselineAreAtomicAndCanBeReopened() {
        Long task=create().task().id();changeSource(task,"完成","CLOSED","completed");var before=currentSource();
        doThrow(new IllegalStateException("模拟审计失败")).when(audit).success(w,"TASK_ISSUE_CONFIRM","TASK",task);
        assertThatThrownBy(()->tx.execute(t->service.confirm(w,task,confirmation(before,IssueSourceConfirmRequest.Action.COMPLETE)))).isInstanceOf(IllegalStateException.class);
        assertThat(tasks.get(w,task).status()).isEqualTo(TaskStatus.TODO);assertThat(currentSource().pending()).isTrue();assertThat(currentSource().confirmationVersion()).isZero();
        doNothing().when(audit).success(w,"TASK_ISSUE_CONFIRM","TASK",task);
        tx.execute(t->service.confirm(w,task,confirmation(before,IssueSourceConfirmRequest.Action.COMPLETE)));
        assertThat(tasks.get(w,task).status()).isEqualTo(TaskStatus.DONE);assertThat(currentSource().pending()).isFalse();
        changeSource(task,"完成","OPEN","reopened");var reopened=currentSource();
        tx.execute(t->service.confirm(w,task,confirmation(reopened,IssueSourceConfirmRequest.Action.REOPEN)));
        assertThat(tasks.get(w,task).status()).isEqualTo(TaskStatus.IN_PROGRESS);assertThat(currentSource().pending()).isFalse();
    }
    @Test void simultaneousConfirmationsAllowOnlyOneSharedDecision() throws Exception {
        Long task=create().task().id();changeSource(task,"待确认","CLOSED",null);var viewed=currentSource();
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> action=()->{start.await();try { tx.execute(t->service.confirm(w,task,confirmation(viewed,IssueSourceConfirmRequest.Action.KEEP)));return true; }
                catch(com.mike.workstation.exception.TaskIssueConflictException ex) { return false; }};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(currentSource().confirmationVersion()).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }
    @Test void confirmationAfterConcurrentSourceCommitRejectsEarlierSnapshot() throws Exception {
        Long task=create().task().id();changeSource(task,"待确认","CLOSED",null);var viewed=currentSource();
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var refresh=pool.submit(()->tx.executeWithoutResult(t->{
                integrations.lockConnection(w,connection);integrations.lockRepository(w,repo);
                var source=sources.lock(w,viewed.id());source.setTitle("后台再次更新");sources.update(source);
                held.countDown();try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("等待超时"); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException(ex); }
            }));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
            var confirmation=pool.submit(()->tx.execute(t->service.confirm(w,task,confirmation(viewed,IssueSourceConfirmRequest.Action.KEEP))));
            release.countDown();refresh.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->confirmation.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(com.mike.workstation.exception.TaskIssueConflictException.class);
            assertThat(currentSource().pending()).isTrue();assertThat(currentSource().confirmationVersion()).isZero();
        } finally { release.countDown();pool.shutdownNow(); }
    }
    @Test void fullTaskSaveAlsoInvalidatesConfirmationVersion() {
        Long task=create().task().id();changeSource(task,"待确认","CLOSED",null);var before=currentSource();
        tx.executeWithoutResult(t->tasks.update(w,task,request()));assertThat(tasks.get(w,task).version()).isEqualTo(before.taskVersion()+1);
        assertThatThrownBy(()->tx.execute(t->service.confirm(w,task,confirmation(before,IssueSourceConfirmRequest.Action.KEEP))))
                .isInstanceOf(com.mike.workstation.exception.TaskIssueConflictException.class);
        assertThat(sources.forTask(w+1000000,task)).isNull();
    }
    /** 子进程领取真实队列后立即终止，不执行正常清理，模拟进程崩溃。 */
    public static class ClaimAndCrash {
        public static void main(String[] args) {
            var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",args[0],"root","");
            var cfg=new Configuration(new Environment("crash",new SpringManagedTransactionFactory(),ds));cfg.setMapUnderscoreToCamelCase(true);cfg.addMapper(IntegrationMapper.class);
            var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg)).getMapper(IntegrationMapper.class);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            var properties=new com.mike.workstation.config.IntegrationProperties(true,"v1",Map.of(),List.of(),15,10,100,180,30);
            var state=new IntegrationStateService(mapper,mock(ProjectMapper.class),mock(WorkspaceMapper.class),mock(WorkspaceAuthorizationService.class),
                    mock(CurrentPrincipalResolver.class),mock(com.mike.workstation.integration.CredentialCipher.class),properties,mock(AuditService.class),mock(IssueSourceMapper.class),mock(GithubWebhookMapper.class));
            IntegrationRun run=tx.execute(status->state.claim());
            if(run==null || !run.getId().toString().equals(args[1])) throw new IllegalStateException("未领取预期作业");
            Runtime.getRuntime().halt(17);
        }
    }
}
