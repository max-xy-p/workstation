package com.mike.workstation.service;

import com.mike.workstation.assembler.TagResponseAssembler;
import com.mike.workstation.domain.Tag;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.TagRequest;
import com.mike.workstation.dto.TagResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.TagMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 标签业务服务。
 *
 * <p>标签和笔记是多对多关系；删除标签时只解除关联，不删除笔记。</p>
 */
public class TagService {
    private final TagMapper tagMapper;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;

    public TagService(
            TagMapper tagMapper,
            WorkspaceAuthorizationService authorization,
            AuditService auditService
    ) {
        this.tagMapper = tagMapper;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    /**
     * 查询所有标签。
     *
     * @return 标签列表
     */
    public List<TagResponse> list(Long workspaceId) {
        authorization.require(workspaceId, WorkspacePermission.TAG_READ);
        return tagMapper.list(workspaceId).stream().map(TagResponseAssembler::toResponse).toList();
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签实体
     */
    public TagResponse get(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.TAG_READ);
        return TagResponseAssembler.toResponse(getEntity(workspaceId, id));
    }

    private Tag getEntity(Long workspaceId, Long id) {
        Tag tag = tagMapper.findById(workspaceId, id);
        RequestChecks.requiredExisting(tag, "标签不存在");
        return tag;
    }

    /**
     * 创建标签。
     *
     * @param request 标签创建请求
     * @return 创建后的标签
     */
    @Transactional
    public TagResponse create(Long workspaceId, TagRequest request) {
        authorization.require(workspaceId, WorkspacePermission.TAG_WRITE);
        validate(workspaceId, request, null);
        Tag tag = new Tag();
        tag.setWorkspaceId(workspaceId);
        tag.setName(request.name().trim());
        LocalDateTime now = LocalDateTime.now();
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        tagMapper.insert(tag);
        auditService.success(workspaceId, "TAG_CREATE", "TAG", tag.getId());
        return get(workspaceId, tag.getId());
    }

    /**
     * 更新标签名称。
     *
     * @param id 标签 ID
     * @param request 标签更新请求
     * @return 更新后的标签
     */
    @Transactional
    public TagResponse update(Long workspaceId, Long id, TagRequest request) {
        authorization.require(workspaceId, WorkspacePermission.TAG_WRITE);
        getEntity(workspaceId, id);
        validate(workspaceId, request, id);
        Tag tag = new Tag();
        tag.setId(id);
        tag.setWorkspaceId(workspaceId);
        tag.setName(request.name().trim());
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.update(tag);
        auditService.success(workspaceId, "TAG_UPDATE", "TAG", id);
        return get(workspaceId, id);
    }

    /**
     * 删除标签并解除笔记关联。
     *
     * @param id 标签 ID
     */
    @Transactional
    public void delete(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.TAG_WRITE);
        getEntity(workspaceId, id);
        tagMapper.deleteTagLinks(workspaceId, id);
        tagMapper.delete(workspaceId, id);
        auditService.success(workspaceId, "TAG_DELETE", "TAG", id);
    }

    /**
     * 校验标签请求。
     */
    private void validate(Long workspaceId, TagRequest request, Long excludeId) {
        RequestChecks.requiredObject(request, "标签请求");
        RequestChecks.requiredText(request.name(), "标签名称");
        String name = request.name().trim();
        RequestChecks.maxLength(name, 80, "标签名称");
        if (tagMapper.countByName(workspaceId, name, excludeId) > 0) {
            throw new BusinessException("标签名称已存在");
        }
    }
}
