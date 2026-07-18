package com.mike.workstation.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Tag;
import com.mike.workstation.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTests {
    @Mock
    private TagMapper tagMapper;
    @InjectMocks
    private TagService tagService;

    @Test
    void deleteClearsNoteLinksBeforeDeletingTag() {
        when(tagMapper.findById(1L)).thenReturn(tag());

        tagService.delete(1L);

        verify(tagMapper).deleteTagLinks(1L);
        verify(tagMapper).delete(1L);
    }

    private Tag tag() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName("Spring");
        return tag;
    }
}
