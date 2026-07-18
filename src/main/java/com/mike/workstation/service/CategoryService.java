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
/**
 * 分类业务服务。
 *
 * <p>分类用于组织笔记；删除分类时需要确认没有笔记仍在使用它。</p>
 */
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final NoteMapper noteMapper;

    public CategoryService(CategoryMapper categoryMapper, NoteMapper noteMapper) {
        this.categoryMapper = categoryMapper;
        this.noteMapper = noteMapper;
    }

    /**
     * 查询所有分类。
     *
     * @return 分类列表
     */
    public List<Category> list() {
        return categoryMapper.list();
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     */
    public Category get(Long id) {
        Category category = categoryMapper.findById(id);
        RequestChecks.requiredExisting(category, "分类不存在");
        return category;
    }

    /**
     * 创建分类。
     *
     * @param request 分类创建请求
     * @return 创建后的分类
     */
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

    /**
     * 更新分类信息。
     *
     * @param id 分类 ID
     * @param request 分类更新请求
     * @return 更新后的分类
     */
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

    /**
     * 删除分类。
     *
     * <p>如果仍有笔记引用该分类，则拒绝删除。</p>
     *
     * @param id 分类 ID
     */
    @Transactional
    public void delete(Long id) {
        get(id);
        if (noteMapper.countByCategoryId(id) > 0) {
            throw new BusinessException("分类下存在笔记，不能直接删除");
        }
        categoryMapper.delete(id);
    }

    /**
     * 校验分类请求。
     */
    private void validate(CategoryRequest request) {
        RequestChecks.requiredObject(request, "分类请求");
        RequestChecks.requiredText(request.name(), "分类名称");
    }
}
