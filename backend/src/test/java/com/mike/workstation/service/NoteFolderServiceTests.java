package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.dto.NoteFolderRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteFolderServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock
    private NoteFolderMapper folderMapper;
    @Mock
    private NoteMapper noteMapper;
    @Mock
    private WorkspaceAuthorizationService authorization;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private NoteFolderService folderService;

    @Test
    void updateRejectsMovingFolderIntoItsDescendant() {
        when(folderMapper.findById(WORKSPACE_ID, 1L)).thenReturn(folder(1L, null));
        when(folderMapper.findById(WORKSPACE_ID, 2L)).thenReturn(folder(2L, 1L));

        assertThatThrownBy(() -> folderService.update(WORKSPACE_ID, 1L, new NoteFolderRequest("Root", 2L)))
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

    private NoteFolder folder(Long id, Long parentId) {
        NoteFolder folder = new NoteFolder();
        folder.setId(id);
        folder.setName("Folder " + id);
        folder.setParentId(parentId);
        return folder;
    }
}
