package com.mike.workstation.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.NoteImportProperties;
import com.mike.workstation.domain.NoteImportDuplicateStrategy;
import com.mike.workstation.domain.NoteRevisionSource;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.dto.NoteRevisionRestoreRequest;
import com.mike.workstation.dto.NoteRevisionSummaryResponse;
import com.mike.workstation.dto.NoteUpdateRequest;
import com.mike.workstation.dto.TagResponse;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.NoteVersionConflictException;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.AuditService;
import com.mike.workstation.service.NoteImportService;
import com.mike.workstation.service.NoteRevisionService;
import com.mike.workstation.service.NoteService;
import com.mike.workstation.service.NoteTagLoader;
import com.mike.workstation.service.WorkspaceAuthorizationService;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 只在显式指定的隔离 MySQL 中验证真实条件更新、历史约束和事务回滚。 */
@EnabledIfSystemProperty(named = "workstation.test.db", matches = ".+")
class NoteRevisionDatabaseTests {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private NoteMapper notes;
    private NoteRevisionMapper revisions;
    private TagMapper tags;
    private NoteFolderMapper folders;
    private ProjectMapper projects;
    private TaskMapper tasks;
    private NoteService service;
    private NoteRevisionService history;
    private final WorkspaceAuthorizationService authorization =
            mock(WorkspaceAuthorizationService.class);
    private final CurrentPrincipalResolver principals = mock(CurrentPrincipalResolver.class);
    private final AuditService audit = mock(AuditService.class);
    private Long workspaceId;
    private String subject;
    @TempDir Path importDirectory;

    @BeforeEach
    void setup() {
        var ds =
                new UnpooledDataSource(
                        "com.mysql.cj.jdbc.Driver",
                        System.getProperty("workstation.test.db"),
                        "root",
                        "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Configuration cfg =
                new Configuration(
                        new Environment("revisions", new SpringManagedTransactionFactory(), ds));
        cfg.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper :
                List.of(
                        NoteMapper.class,
                        NoteRevisionMapper.class,
                        TagMapper.class,
                        NoteFolderMapper.class,
                        ProjectMapper.class,
                        TaskMapper.class)) {
            cfg.addMapper(mapper);
        }
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg));
        notes = session.getMapper(NoteMapper.class);
        revisions = session.getMapper(NoteRevisionMapper.class);
        tags = session.getMapper(TagMapper.class);
        folders = session.getMapper(NoteFolderMapper.class);
        projects = session.getMapper(ProjectMapper.class);
        tasks = session.getMapper(TaskMapper.class);
        subject = "revision-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO security_principals VALUES (?,'USER','revision-author','历史作者',NOW(),NOW())",
                subject);
        jdbc.update(
                "INSERT INTO workspaces (name,type,created_by_subject,created_at,updated_at) VALUES (?,'SHARED',?,NOW(),NOW())",
                subject,
                subject);
        workspaceId =
                jdbc.queryForObject(
                        "SELECT id FROM workspaces WHERE created_by_subject=?",
                        Long.class,
                        subject);
        when(principals.require())
                .thenReturn(
                        new CurrentPrincipal(
                                subject, PrincipalType.USER, "revision-author", "历史作者", Set.of()));
        history = new NoteRevisionService(revisions, notes, authorization, principals);
        service = serviceWith(history, tags);
    }

    private NoteService serviceWith(NoteRevisionService writer, TagMapper tagMapper) {
        return new NoteService(
                notes,
                folders,
                projects,
                tagMapper,
                tasks,
                new NoteTagLoader(tagMapper),
                principals,
                authorization,
                audit,
                writer);
    }

    @AfterEach
    void cleanup() {
        if (workspaceId == null) {
            return;
        }
        jdbc.update("DELETE FROM tasks WHERE workspace_id=?", workspaceId);
        jdbc.update("DELETE FROM notes WHERE workspace_id=?", workspaceId);
        jdbc.update("DELETE FROM tags WHERE workspace_id=?", workspaceId);
        jdbc.update("DELETE FROM note_folders WHERE workspace_id=?", workspaceId);
        jdbc.update("DELETE FROM projects WHERE workspace_id=?", workspaceId);
        jdbc.update("DELETE FROM workspaces WHERE id=?", workspaceId);
        jdbc.update("DELETE FROM security_principals WHERE subject_id=?", subject);
    }

    private NoteDetailResponse create() {
        return tx.execute(
                status ->
                        service.create(
                                workspaceId, new NoteRequest("标题", "A", null, null, List.of())));
    }

    private NoteDetailResponse update(NoteDetailResponse note, String content) {
        return tx.execute(
                status ->
                        service.update(
                                workspaceId,
                                note.id(),
                                new NoteUpdateRequest(
                                        note.title(),
                                        content,
                                        note.folderId(),
                                        note.projectId(),
                                        note.tags().stream().map(TagResponse::id).toList(),
                                        note.version())));
    }

    private long count(Long noteId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM note_revisions WHERE workspace_id=? AND note_id=?",
                Long.class,
                workspaceId,
                noteId);
    }

    @Test
    void snapshotsPreserveChangesDeduplicateContentAndPinPages() {
        var first = create();
        var second = update(first, "B");
        var same = update(second, "B");
        var back = update(same, "A");
        assertThat(back.version()).isEqualTo(4L);
        assertThat(count(first.id())).isEqualTo(3);
        var all = history.list(workspaceId, first.id(), null, 0, 20);
        assertThat(all.items())
                .extracting(NoteRevisionSummaryResponse::noteVersion)
                .containsExactly(4L, 2L, 1L);
        var pinned = history.list(workspaceId, first.id(), 2L, 0, 1);
        assertThat(pinned.total()).isEqualTo(2);
        assertThat(pinned.items().get(0).actorDisplayName()).isEqualTo("历史作者");
        assertThat(history.list(workspaceId, first.id(), 2L, 1, 1).items().get(0).noteVersion())
                .isEqualTo(1L);
        assertThat(history.get(workspaceId, first.id(), all.items().get(0).id()).content())
                .isEqualTo("A");
        assertThatThrownBy(() -> history.get(workspaceId + 1, first.id(), all.items().get(0).id()))
                .isInstanceOf(NotFoundException.class);
        var other = create();
        assertThatThrownBy(() -> history.get(workspaceId, other.id(), all.items().get(0).id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void restoreKeepsCurrentAssociationsAndExplicitlyRecordsIdenticalRestore() {
        var original = create();
        Long originalRevision =
                history.list(workspaceId, original.id(), null, 0, 20).items().get(0).id();
        jdbc.update(
                "INSERT INTO projects (workspace_id,name,status,created_at,updated_at) VALUES (?,'项目','ACTIVE',NOW(),NOW())",
                workspaceId);
        Long project =
                jdbc.queryForObject(
                        "SELECT id FROM projects WHERE workspace_id=?", Long.class, workspaceId);
        jdbc.update(
                "INSERT INTO note_folders (workspace_id,name,created_at,updated_at) VALUES (?,'文件夹',NOW(),NOW())",
                workspaceId);
        Long folder =
                jdbc.queryForObject(
                        "SELECT id FROM note_folders WHERE workspace_id=?",
                        Long.class,
                        workspaceId);
        jdbc.update(
                "INSERT INTO tags (workspace_id,name,created_at,updated_at) VALUES (?,'标签',NOW(),NOW())",
                workspaceId);
        Long tag =
                jdbc.queryForObject(
                        "SELECT id FROM tags WHERE workspace_id=?", Long.class, workspaceId);
        tx.execute(
                status ->
                        service.update(
                                workspaceId,
                                original.id(),
                                new NoteUpdateRequest(
                                        "新标题", "B", folder, project, List.of(tag), 1L)));
        jdbc.update(
                "INSERT INTO tasks (workspace_id,title,project_id,evidence_note_id,status,priority,created_at,updated_at) VALUES (?,'依据任务',?,?,'TODO','MEDIUM',NOW(),NOW())",
                workspaceId,
                project,
                original.id());
        var restored =
                tx.execute(
                        status ->
                                service.restoreRevision(
                                        workspaceId,
                                        original.id(),
                                        originalRevision,
                                        new NoteRevisionRestoreRequest(2L)));
        assertThat(restored.title()).isEqualTo("标题");
        assertThat(restored.content()).isEqualTo("A");
        assertThat(restored.folderId()).isEqualTo(folder);
        assertThat(restored.projectId()).isEqualTo(project);
        assertThat(restored.tags()).extracting(TagResponse::id).containsExactly(tag);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT evidence_note_id FROM tasks WHERE workspace_id=?",
                                Long.class,
                                workspaceId))
                .isEqualTo(original.id());
        tx.execute(
                status ->
                        service.restoreRevision(
                                workspaceId,
                                original.id(),
                                originalRevision,
                                new NoteRevisionRestoreRequest(3L)));
        assertThat(count(original.id())).isEqualTo(4);
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        status ->
                                                service.restoreRevision(
                                                        workspaceId,
                                                        original.id(),
                                                        originalRevision,
                                                        new NoteRevisionRestoreRequest(2L))))
                .isInstanceOf(NoteVersionConflictException.class);
        assertThat(count(original.id())).isEqualTo(4);
    }

    @Test
    void failingSnapshotAndFailingLaterStepBothRollbackCurrentContent() {
        var original = create();
        var failingMapper =
                mock(NoteRevisionMapper.class, AdditionalAnswers.delegatesTo(revisions));
        doThrow(new IllegalStateException("模拟历史写入失败")).when(failingMapper).insert(any());
        var failingWriter =
                new NoteRevisionService(failingMapper, notes, authorization, principals);
        var broken = serviceWith(failingWriter, tags);
        var request = new NoteUpdateRequest("新标题", "失败内容", null, null, List.of(), 1L);
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        status ->
                                                broken.update(workspaceId, original.id(), request)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.get(workspaceId, original.id()).version()).isEqualTo(1L);
        assertThat(service.get(workspaceId, original.id()).content()).isEqualTo("A");
        assertThat(count(original.id())).isEqualTo(1);
        var failingTags = mock(TagMapper.class, AdditionalAnswers.delegatesTo(tags));
        doThrow(new IllegalStateException("模拟后续标签写入失败"))
                .when(failingTags)
                .deleteNoteTags(workspaceId, original.id());
        var afterHistory = serviceWith(history, failingTags);
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        status ->
                                                afterHistory.update(
                                                        workspaceId, original.id(), request)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.get(workspaceId, original.id()).version()).isEqualTo(1L);
        assertThat(count(original.id())).isEqualTo(1);
    }

    @Test
    void concurrentTransactionsOnlyCommitOneVersionAndOneNewSnapshot() throws Exception {
        var original = create();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results =
                    List.of("B", "C").stream()
                            .map(
                                    content ->
                                            executor.submit(
                                                    () -> {
                                                        ready.countDown();
                                                        if (!start.await(10, TimeUnit.SECONDS)) {
                                                            throw new IllegalStateException(
                                                                    "并发测试等待超时");
                                                        }
                                                        try {
                                                            update(original, content);
                                                            return "成功";
                                                        } catch (NoteVersionConflictException ex) {
                                                            return "冲突";
                                                        }
                                                    }))
                            .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            List.of(
                                    results.get(0).get(20, TimeUnit.SECONDS),
                                    results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("成功", "冲突");
            assertThat(service.get(workspaceId, original.id()).version()).isEqualTo(2L);
            assertThat(count(original.id())).isEqualTo(2);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void trashRetainsSnapshotsAndPermanentDeleteCascadesThem() {
        var original = create();
        Long revision = history.list(workspaceId, original.id(), null, 0, 20).items().get(0).id();
        tx.executeWithoutResult(status -> service.delete(workspaceId, original.id()));
        assertThat(count(original.id())).isEqualTo(1);
        assertThatThrownBy(() -> history.get(workspaceId, original.id(), revision))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        status ->
                                                service.restoreRevision(
                                                        workspaceId,
                                                        original.id(),
                                                        revision,
                                                        new NoteRevisionRestoreRequest(2L))))
                .isInstanceOf(NotFoundException.class);
        tx.execute(status -> service.restore(workspaceId, original.id()));
        assertThat(history.get(workspaceId, original.id(), revision).content()).isEqualTo("A");
        tx.executeWithoutResult(status -> service.delete(workspaceId, original.id()));
        tx.executeWithoutResult(status -> service.permanentlyDelete(workspaceId, original.id()));
        assertThat(count(original.id())).isZero();
    }

    @Test
    void importsRecordOriginAndFailedImportLeavesNoPartialNote() throws Exception {
        Files.writeString(importDirectory.resolve("entry.md"), "# 导入标题\n正文");
        var properties = new NoteImportProperties(List.of(importDirectory.toString()), 1024L);
        var importer =
                new NoteImportService(
                        properties, notes, folders, projects, tags, authorization, audit, history);
        var request =
                new NoteImportRequest(
                        importDirectory.toString(),
                        null,
                        null,
                        List.of(),
                        NoteImportDuplicateStrategy.CREATE_NEW);
        var result = tx.execute(status -> importer.importMarkdown(workspaceId, request));
        Long id = result.items().get(0).noteId();
        assertThat(history.list(workspaceId, id, null, 0, 20).items().get(0).source())
                .isEqualTo(NoteRevisionSource.IMPORT);
        var failing = mock(NoteRevisionMapper.class, AdditionalAnswers.delegatesTo(revisions));
        doThrow(new IllegalStateException("模拟快照失败")).when(failing).insert(any());
        var failedImporter =
                new NoteImportService(
                        properties,
                        notes,
                        folders,
                        projects,
                        tags,
                        authorization,
                        audit,
                        new NoteRevisionService(failing, notes, authorization, principals));
        assertThat(
                        tx.execute(status -> failedImporter.importMarkdown(workspaceId, request))
                                .failedCount())
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notes WHERE workspace_id=?",
                                Long.class,
                                workspaceId))
                .isEqualTo(1);
        assertThat(count(id)).isEqualTo(1);
    }
}
