package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Category;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.CategoryMapper;
import com.mike.workstation.mapper.NoteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private NoteMapper noteMapper;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    void deleteRejectsCategoryWithNotes() {
        when(categoryMapper.findById(1L)).thenReturn(category());
        when(noteMapper.countByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在笔记");
    }

    @Test
    void deleteRemovesEmptyCategory() {
        when(categoryMapper.findById(1L)).thenReturn(category());
        when(noteMapper.countByCategoryId(1L)).thenReturn(0L);

        categoryService.delete(1L);

        verify(categoryMapper).delete(1L);
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Java");
        return category;
    }
}
