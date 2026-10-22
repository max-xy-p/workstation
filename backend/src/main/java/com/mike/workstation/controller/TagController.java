package com.mike.workstation.controller;

import com.mike.workstation.dto.TagRequest;
import com.mike.workstation.dto.TagResponse;
import com.mike.workstation.service.TagService;

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

import java.util.List;

/**
 * 标签接口控制器。
 *
 * <p>标签用于给笔记打多个主题标记，笔记和标签之间是多对多关系。
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 查询全部标签。
     *
     * @return 按名称排序的标签列表
     */
    @GetMapping
    public List<TagResponse> list(@PathVariable Long workspaceId) {
        return tagService.list(workspaceId);
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签详情
     */
    @GetMapping("/{id}")
    public TagResponse get(@PathVariable Long workspaceId, @PathVariable Long id) {
        return tagService.get(workspaceId, id);
    }

    /**
     * 创建标签。
     *
     * @param request 标签创建请求，名称必填
     * @return 创建后的标签数据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@PathVariable Long workspaceId, @RequestBody TagRequest request) {
        return tagService.create(workspaceId, request);
    }

    /**
     * 更新标签名称。
     *
     * @param id 标签 ID
     * @param request 标签更新请求
     * @return 更新后的标签数据
     */
    @PutMapping("/{id}")
    public TagResponse update(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody TagRequest request) {
        return tagService.update(workspaceId, id, request);
    }

    /**
     * 删除标签。
     *
     * <p>删除标签前会先解除它与笔记的关联，笔记本身不会被删除。
     *
     * @param id 标签 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long id) {
        tagService.delete(workspaceId, id);
    }
}
