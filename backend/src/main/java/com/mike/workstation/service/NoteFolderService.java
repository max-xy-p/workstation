package com.mike.workstation.service;

import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.dto.NoteFolderRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteFolderService {
    private final NoteFolderMapper folderMapper;
    private final NoteMapper noteMapper;

    public NoteFolderService(NoteFolderMapper folderMapper, NoteMapper noteMapper) {
        this.folderMapper = folderMapper;
        this.noteMapper = noteMapper;
    }

    public List<NoteFolder> list() {
        return folderMapper.list();
    }

    public NoteFolder get(Long id) {
        NoteFolder folder = folderMapper.findById(id);
        RequestChecks.requiredExisting(folder, "文件夹不存在");
        return folder;
    }

    @Transactional
    public NoteFolder create(NoteFolderRequest request) {
        validateRequest(request, null);
        NoteFolder folder = new NoteFolder();
        folder.setName(request.name().trim());
        folder.setParentId(request.parentId());
        LocalDateTime now = LocalDateTime.now();
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        folderMapper.insert(folder);
        return get(folder.getId());
    }

    @Transactional
    public NoteFolder update(Long id, NoteFolderRequest request) {
        get(id);
        validateRequest(request, id);
        ensureAcyclic(id, request.parentId());

        NoteFolder folder = new NoteFolder();
        folder.setId(id);
        folder.setName(request.name().trim());
        folder.setParentId(request.parentId());
        folder.setUpdatedAt(LocalDateTime.now());
        folderMapper.update(folder);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        if (folderMapper.countChildren(id) > 0) {
            throw new BusinessException("文件夹下存在子文件夹，不能直接删除");
        }
        if (noteMapper.countByFolderId(id) > 0) {
            throw new BusinessException("文件夹下存在笔记，不能直接删除");
        }
        folderMapper.delete(id);
    }

    private void validateRequest(NoteFolderRequest request, Long excludeId) {
        RequestChecks.requiredObject(request, "文件夹请求");
        RequestChecks.requiredText(request.name(), "文件夹名称");
        if (request.parentId() != null) {
            get(request.parentId());
        }
        String name = request.name().trim();
        if (name.length() > 120) {
            throw new BusinessException("文件夹名称不能超过 120 个字符");
        }
        if (folderMapper.countSiblingName(name, request.parentId(), excludeId) > 0) {
            throw new BusinessException("同级目录下已存在同名文件夹");
        }
    }

    private void ensureAcyclic(Long folderId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null) {
            if (currentId.equals(folderId)) {
                throw new BusinessException("不能把文件夹移动到自身或其子文件夹中");
            }
            if (!visited.add(currentId)) {
                throw new BusinessException("文件夹层级存在循环");
            }
            currentId = get(currentId).getParentId();
        }
    }
}
