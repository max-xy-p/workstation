package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.dto.NoteFolderRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteFolderServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock private NoteFolderMapper folderMapper;
    @Mock private NoteMapper noteMapper;
    @Mock private WorkspaceAuthorizationService authorization;
    @Mock private AuditService auditService;
    @InjectMocks private NoteFolderService folderService;

    @Test
    void updateRejectsMovingFolderIntoItsDescendant() {
        when(folderMapper.findById(WORKSPACE_ID, 1L)).thenReturn(folder(1L, null));
        when(folderMapper.findById(WORKSPACE_ID, 2L)).thenReturn(folder(2L, 1L));

        assertThatThrownBy(
                        () ->
                                folderService.update(
                                        WORKSPACE_ID, 1L, new NoteFolderRequest("Root", 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子文件夹");
    }

    @Test
    void deleteRejectsFolderWithNotes() {
        when(folderMapper.findById(WORKSPACE_ID, 1L)).thenReturn(folder(1L, null));
        when(folderMapper.countChildren(WORKSPACE_ID, 1L)).thenReturn(0L);
        when(noteMapper.countByFolderId(WORKSPACE_ID, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> folderService.delete(WORKSPACE_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在笔记");
    }

    @Test
    void deleteRemovesEmptyFolder() {
        when(folderMapper.findById(WORKSPACE_ID, 1L)).thenReturn(folder(1L, null));

        folderService.delete(WORKSPACE_ID, 1L);

        verify(folderMapper).delete(WORKSPACE_ID, 1L);
    }

    @Test
    void createUnderExistingParentTrimsNameAndAudits() {
        when(folderMapper.findById(WORKSPACE_ID, 2L)).thenReturn(folder(2L, null));
        Mockito.doAnswer(
                        invocation -> {
                            NoteFolder inserted = invocation.getArgument(0);
                            inserted.setId(3L);
                            return 1;
                        })
                .when(folderMapper)
                .insert(any(NoteFolder.class));
        when(folderMapper.findById(WORKSPACE_ID, 3L)).thenReturn(folder(3L, 2L));

        var created = folderService.create(WORKSPACE_ID, new NoteFolderRequest("  新目录  ", 2L));

        assertThat(created.id()).isEqualTo(3L);
        var captor = ArgumentCaptor.forClass(NoteFolder.class);
        verify(folderMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新目录");
        assertThat(captor.getValue().getParentId()).isEqualTo(2L);
        verify(auditService).success(WORKSPACE_ID, "FOLDER_CREATE", "NOTE_FOLDER", 3L);
    }

    @Test
    void updateAllowsMovingFolderUnderAnotherBranch() {
        when(folderMapper.findById(WORKSPACE_ID, 1L)).thenReturn(folder(1L, null));
        when(folderMapper.findById(WORKSPACE_ID, 2L)).thenReturn(folder(2L, null));

        folderService.update(WORKSPACE_ID, 1L, new NoteFolderRequest("  调整后  ", 2L));

        var captor = ArgumentCaptor.forClass(NoteFolder.class);
        verify(folderMapper).update(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("调整后");
        assertThat(captor.getValue().getParentId()).isEqualTo(2L);
        verify(auditService).success(WORKSPACE_ID, "FOLDER_UPDATE", "NOTE_FOLDER", 1L);
    }

    @Test
    void duplicateSiblingNameIsRejectedBeforeInsert() {
        when(folderMapper.countSiblingName(WORKSPACE_ID, "重复", null, null)).thenReturn(1L);

        assertThatThrownBy(
                        () ->
                                folderService.create(
                                        WORKSPACE_ID, new NoteFolderRequest(" 重复 ", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名");
        verify(folderMapper, never()).insert(any(NoteFolder.class));
    }

    @Test
    void deleteRejectsFolderWithChildren() {
        when(folderMapper.findById(WORKSPACE_ID, 1L)).thenReturn(folder(1L, null));
        when(folderMapper.countChildren(WORKSPACE_ID, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> folderService.delete(WORKSPACE_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子文件夹");
        verify(folderMapper, never()).delete(WORKSPACE_ID, 1L);
    }

    private NoteFolder folder(Long id, Long parentId) {
        NoteFolder folder = new NoteFolder();
        folder.setId(id);
        folder.setName("Folder " + id);
        folder.setParentId(parentId);
        return folder;
    }
}
