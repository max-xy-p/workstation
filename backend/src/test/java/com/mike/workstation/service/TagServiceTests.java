package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Tag;
import com.mike.workstation.dto.TagRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.mapper.TagMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock private TagMapper tagMapper;
    @Mock private WorkspaceAuthorizationService authorization;
    @Mock private AuditService auditService;
    @InjectMocks private TagService tagService;

    @Test
    void deleteClearsNoteLinksBeforeDeletingTag() {
        when(tagMapper.findById(WORKSPACE_ID, 1L)).thenReturn(tag());

        tagService.delete(WORKSPACE_ID, 1L);

        verify(tagMapper).deleteTagLinks(WORKSPACE_ID, 1L);
        verify(tagMapper).delete(WORKSPACE_ID, 1L);
    }

    @Test
    void createRejectsDuplicateNormalizedName() {
        when(tagMapper.countByName(WORKSPACE_ID, "Spring", null)).thenReturn(1L);

        assertThatThrownBy(() -> tagService.create(WORKSPACE_ID, new TagRequest("  Spring  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void createNormalizesNameAndAuditsPersistedTag() {
        Mockito.doAnswer(
                        invocation -> {
                            Tag inserted = invocation.getArgument(0);
                            inserted.setId(2L);
                            return 1;
                        })
                .when(tagMapper)
                .insert(any(Tag.class));
        Tag persisted = tag();
        persisted.setId(2L);
        when(tagMapper.findById(WORKSPACE_ID, 2L)).thenReturn(persisted);

        var created = tagService.create(WORKSPACE_ID, new TagRequest("  Spring  "));

        assertThat(created.id()).isEqualTo(2L);
        var captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Spring");
        verify(auditService).success(WORKSPACE_ID, "TAG_CREATE", "TAG", 2L);
    }

    @Test
    void updateChangesNameWithoutChangingWorkspace() {
        when(tagMapper.findById(WORKSPACE_ID, 1L)).thenReturn(tag());

        tagService.update(WORKSPACE_ID, 1L, new TagRequest("  Architecture  "));

        var captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).update(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Architecture");
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        verify(auditService).success(WORKSPACE_ID, "TAG_UPDATE", "TAG", 1L);
    }

    @Test
    void missingTagCannotBeUpdated() {
        assertThatThrownBy(() -> tagService.update(WORKSPACE_ID, 99L, new TagRequest("New")))
                .isInstanceOf(NotFoundException.class);
        verify(tagMapper, never()).update(any(Tag.class));
    }

    private Tag tag() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName("Spring");
        return tag;
    }
}
