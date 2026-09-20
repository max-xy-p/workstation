package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Tag;
import com.mike.workstation.dto.TagRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock
    private TagMapper tagMapper;
    @Mock
    private WorkspaceAuthorizationService authorization;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private TagService tagService;

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

    private Tag tag() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName("Spring");
        return tag;
    }
}
