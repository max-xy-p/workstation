package com.mike.workstation.controller;

import com.mike.workstation.domain.Category;
import com.mike.workstation.dto.CategoryRequest;
import com.mike.workstation.service.CategoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
/**
 * 分类接口控制器。
 *
 * <p>分类用于组织技术笔记，一篇笔记最多属于一个分类。</p>
 */
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询全部分类。
     *
     * @return 按名称排序的分类列表
     */
    @GetMapping
    public List<Category> list() {
        return categoryService.list();
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     */
    @GetMapping("/{id}")
    public Category get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    /**
     * 创建分类。
     *
     * @param request 分类创建请求，名称必填
     * @return 创建后的分类数据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    /**
     * 更新分类信息。
     *
     * @param id 分类 ID
     * @param request 分类更新请求
     * @return 更新后的分类数据
     */
    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    /**
     * 删除分类。
     *
     * <p>如果分类下还有笔记，Service 层会拒绝删除并返回明确错误。</p>
     *
     * @param id 分类 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
