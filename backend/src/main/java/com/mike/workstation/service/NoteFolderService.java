package com.mike.workstation.service;

import com.mike.workstation.assembler.NoteFolderResponseAssembler;
import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NoteFolderRequest;
import com.mike.workstation.dto.NoteFolderResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteFolderService {
    private static final String NOTE_FOLDER_RESOURCE = "NOTE_FOLDER";
    private final NoteFolderMapper folderMapper;
    private final NoteMapper noteMapper;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;

    public NoteFolderService(
            NoteFolderMapper folderMapper,
            NoteMapper noteMapper,
            WorkspaceAuthorizationService authorization,
            AuditService auditService
    ) {
        this.folderMapper = folderMapper;
        this.noteMapper = noteMapper;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    public List<NoteFolderResponse> list(Long workspaceId) {
        authorization.require(workspaceId, WorkspacePermission.FOLDER_READ);
        return folderMapper.list(workspaceId).stream().map(NoteFolderResponseAssembler::toResponse).toList();
    }

    public NoteFolderResponse get(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.FOLDER_READ);
        return NoteFolderResponseAssembler.toResponse(getEntity(workspaceId, id));
    }

    private NoteFolder getEntity(Long workspaceId, Long id) {
        NoteFolder folder = folderMapper.findById(workspaceId, id);
        RequestChecks.requiredExisting(folder, "文件夹不存在");
        return folder;
    }

    @Transactional
    public NoteFolderResponse create(Long workspaceId, NoteFolderRequest request) {
        authorization.require(workspaceId, WorkspacePermission.FOLDER_WRITE);
        validateRequest(workspaceId, request, null);
        NoteFolder folder = new NoteFolder();
        folder.setWorkspaceId(workspaceId);
        folder.setName(request.name().trim());
        folder.setParentId(request.parentId());
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        folderMapper.insert(folder);
        auditService.success(workspaceId, "FOLDER_CREATE", NOTE_FOLDER_RESOURCE, folder.getId());
        return get(workspaceId, folder.getId());
    }

    @Transactional
    public NoteFolderResponse update(Long workspaceId, Long id, NoteFolderRequest request) {
        authorization.require(workspaceId, WorkspacePermission.FOLDER_WRITE);
        getEntity(workspaceId, id);
        validateRequest(workspaceId, request, id);
        ensureAcyclic(workspaceId, id, request.parentId());

        NoteFolder folder = new NoteFolder();
        folder.setId(id);
        folder.setWorkspaceId(workspaceId);
        folder.setName(request.name().trim());
        folder.setParentId(request.parentId());
        folder.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        folderMapper.update(folder);
        auditService.success(workspaceId, "FOLDER_UPDATE", NOTE_FOLDER_RESOURCE, id);
        return get(workspaceId, id);
    }

    @Transactional
    public void delete(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.FOLDER_WRITE);
        getEntity(workspaceId, id);
        if (folderMapper.countChildren(workspaceId, id) > 0) {
            throw new BusinessException("文件夹下存在子文件夹，不能直接删除");
        }
        if (noteMapper.countByFolderId(workspaceId, id) > 0) {
            throw new BusinessException("文件夹下存在笔记，不能直接删除");
        }
        folderMapper.delete(workspaceId, id);
        auditService.success(workspaceId, "FOLDER_DELETE", NOTE_FOLDER_RESOURCE, id);
    }

    private void validateRequest(Long workspaceId, NoteFolderRequest request, Long excludeId) {
        RequestChecks.requiredObject(request, "文件夹请求");
        RequestChecks.requiredText(request.name(), "文件夹名称");
        if (request.parentId() != null) {
            getEntity(workspaceId, request.parentId());
        }
        String name = request.name().trim();
        if (name.length() > 120) {
            throw new BusinessException("文件夹名称不能超过 120 个字符");
        }
        if (folderMapper.countSiblingName(workspaceId, name, request.parentId(), excludeId) > 0) {
            throw new BusinessException("同级目录下已存在同名文件夹");
        }
    }

    private void ensureAcyclic(Long workspaceId, Long folderId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null) {
            if (currentId.equals(folderId)) {
                throw new BusinessException("不能把文件夹移动到自身或其子文件夹中");
            }
            if (!visited.add(currentId)) {
                throw new BusinessException("文件夹层级存在循环");
            }
            currentId = getEntity(workspaceId, currentId).getParentId();
        }
    }
}
