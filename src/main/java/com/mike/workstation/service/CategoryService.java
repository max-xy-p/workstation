package com.mike.workstation.service;

import com.mike.workstation.domain.Category;
import com.mike.workstation.dto.CategoryRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.CategoryMapper;
import com.mike.workstation.mapper.NoteMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final NoteMapper noteMapper;

    public CategoryService(CategoryMapper categoryMapper, NoteMapper noteMapper) {
        this.categoryMapper = categoryMapper;
        this.noteMapper = noteMapper;
    }

    public List<Category> list() {
        return categoryMapper.list();
    }

    public Category get(Long id) {
        Category category = categoryMapper.findById(id);
        RequestChecks.requiredExisting(category, "分类不存在");
        return category;
    }

    @Transactional
    public Category create(CategoryRequest request) {
        validate(request);
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        LocalDateTime now = LocalDateTime.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        categoryMapper.insert(category);
        return get(category.getId());
    }

    @Transactional
    public Category update(Long id, CategoryRequest request) {
        validate(request);
        get(id);
        Category category = new Category();
        category.setId(id);
        category.setName(request.name());
        category.setDescription(request.description());
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.update(category);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        if (noteMapper.countByCategoryId(id) > 0) {
            throw new BusinessException("分类下存在笔记，不能直接删除");
        }
        categoryMapper.delete(id);
    }

    private void validate(CategoryRequest request) {
        RequestChecks.requiredObject(request, "分类请求");
        RequestChecks.requiredText(request.name(), "分类名称");
    }
}
