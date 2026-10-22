package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mike.workstation.config.NoteImportProperties;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.domain.NoteImportDuplicateStrategy;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.Tag;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class NoteImportServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @TempDir Path allowedRoot;
    @Mock NoteMapper noteMapper;
    @Mock NoteFolderMapper folderMapper;
    @Mock ProjectMapper projectMapper;
    @Mock TagMapper tagMapper;
    @Mock WorkspaceAuthorizationService authorization;
    @Mock AuditService auditService;

    @Mock NoteRevisionService revisions;

    private NoteImportService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(new NoteImportProperties(List.of(allowedRoot.toString()), 1024L));
    }

    @Test
    void recursivelyImportsMarkdownAndNormalizesTagLinks() throws IOException {
        Path nested = Files.createDirectory(allowedRoot.resolve("nested"));
        Files.writeString(nested.resolve("entry.markdown"), "# 导入标题\n正文");
        Files.writeString(allowedRoot.resolve("ignored.txt"), "忽略");
        when(folderMapper.findById(WORKSPACE_ID, 3L)).thenReturn(new NoteFolder());
        when(projectMapper.findById(WORKSPACE_ID, 4L)).thenReturn(new Project());
        when(tagMapper.findByIds(WORKSPACE_ID, List.of(5L, 6L)))
                .thenReturn(List.of(new Tag(), new Tag()));
        doAnswer(
                        invocation -> {
                            Note note = invocation.getArgument(0);
                            note.setId(11L);
                            return 1;
                        })
                .when(noteMapper)
                .insert(any(Note.class));

        var result =
                service.importMarkdown(
                        WORKSPACE_ID,
                        new NoteImportRequest(
                                allowedRoot.toString(),
                                3L,
                                4L,
                                Arrays.asList(5L, null, 5L, 6L),
                                NoteImportDuplicateStrategy.CREATE_NEW));

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.items().get(0).title()).isEqualTo("导入标题");
        assertThat(result.items().get(0).noteId()).isEqualTo(11L);
        verify(tagMapper).insertNoteTag(WORKSPACE_ID, 11L, 5L);
        verify(tagMapper).insertNoteTag(WORKSPACE_ID, 11L, 6L);
        verify(auditService).success(WORKSPACE_ID, "NOTE_IMPORT", "NOTE", null);
    }

    @Test
    void skipsDuplicateTitleButContinuesWithOtherFiles() throws IOException {
        Files.writeString(allowedRoot.resolve("a.md"), "# 已存在\n内容");
        Files.writeString(allowedRoot.resolve("b.md"), "# 新笔记\n内容");
        when(noteMapper.countByTitle(WORKSPACE_ID, "已存在")).thenReturn(1L);

        var result =
                service.importMarkdown(
                        WORKSPACE_ID, request(allowedRoot, NoteImportDuplicateStrategy.SKIP_TITLE));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.items())
                .extracting(item -> item.status())
                .containsExactly("SKIPPED", "IMPORTED");
        verify(noteMapper).countByTitle(WORKSPACE_ID, "新笔记");
    }

    @Test
    void oversizedFileFailsWithoutStoppingNextImport() throws IOException {
        service = serviceWith(new NoteImportProperties(List.of(allowedRoot.toString()), 12L));
        Files.writeString(allowedRoot.resolve("a.md"), "# 太大的文件\n正文很长");
        Files.writeString(allowedRoot.resolve("b.md"), "# 小\n文");

        var result =
                service.importMarkdown(
                        WORKSPACE_ID, request(allowedRoot, NoteImportDuplicateStrategy.CREATE_NEW));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.items().get(0).message()).contains("大小限制");
    }

    @Test
    void tagInsertFailureCleansPartialNoteAndContinues() throws IOException {
        Files.writeString(allowedRoot.resolve("a.md"), "# 失败\n正文");
        Files.writeString(allowedRoot.resolve("b.md"), "# 成功\n正文");
        when(tagMapper.findByIds(WORKSPACE_ID, List.of(5L))).thenReturn(List.of(new Tag()));
        doAnswer(
                        invocation -> {
                            Note note = invocation.getArgument(0);
                            note.setId("失败".equals(note.getTitle()) ? 11L : 12L);
                            return 1;
                        })
                .when(noteMapper)
                .insert(any(Note.class));
        doThrow(new IllegalStateException("关联写入失败"))
                .when(tagMapper)
                .insertNoteTag(WORKSPACE_ID, 11L, 5L);

        var result =
                service.importMarkdown(
                        WORKSPACE_ID,
                        new NoteImportRequest(
                                allowedRoot.toString(),
                                null,
                                null,
                                List.of(5L),
                                NoteImportDuplicateStrategy.CREATE_NEW));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.items().get(0).message()).contains("关联写入失败");
        verify(tagMapper).deleteNoteTags(WORKSPACE_ID, 11L);
        verify(noteMapper).deleteIncompleteImport(WORKSPACE_ID, 11L);
        verify(noteMapper, never()).deleteIncompleteImport(WORKSPACE_ID, 12L);
    }

    @Test
    void rejectsPathsOutsideAllowlistAndMissingPaths() {
        Path outside = allowedRoot.getParent().resolve("outside.md");
        assertThatThrownBy(
                        () ->
                                service.importMarkdown(
                                        WORKSPACE_ID,
                                        request(outside, NoteImportDuplicateStrategy.CREATE_NEW)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("允许目录");
        assertThatThrownBy(
                        () ->
                                service.importMarkdown(
                                        WORKSPACE_ID,
                                        request(
                                                allowedRoot.resolve("missing.md"),
                                                NoteImportDuplicateStrategy.CREATE_NEW)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        verifyNoInteractions(noteMapper, auditService);
    }

    @Test
    void rejectsUnknownTagBeforeReadingFile() throws IOException {
        Path source = Files.writeString(allowedRoot.resolve("entry.md"), "# 标题");
        when(tagMapper.findByIds(WORKSPACE_ID, List.of(5L))).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.importMarkdown(
                                        WORKSPACE_ID,
                                        new NoteImportRequest(
                                                source.toString(),
                                                null,
                                                null,
                                                List.of(5L),
                                                NoteImportDuplicateStrategy.CREATE_NEW)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效标签");
        verifyNoInteractions(noteMapper, auditService);
    }

    @Test
    void deniesImportBeforeAccessingPath() {
        doThrow(new BusinessException("无权限"))
                .when(authorization)
                .require(WORKSPACE_ID, WorkspacePermission.NOTE_IMPORT);

        assertThatThrownBy(
                        () ->
                                service.importMarkdown(
                                        WORKSPACE_ID,
                                        request(
                                                allowedRoot.resolve("missing.md"),
                                                NoteImportDuplicateStrategy.CREATE_NEW)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限");
        verifyNoInteractions(noteMapper, auditService);
    }

    private NoteImportService serviceWith(NoteImportProperties properties) {
        return new NoteImportService(
                properties,
                noteMapper,
                folderMapper,
                projectMapper,
                tagMapper,
                authorization,
                auditService,
                revisions);
    }

    private NoteImportRequest request(Path path, NoteImportDuplicateStrategy strategy) {
        return new NoteImportRequest(path.toString(), null, null, null, strategy);
    }
}
