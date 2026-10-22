package com.mike.workstation.controller;

import com.mike.workstation.dto.NoteFolderRequest;
import com.mike.workstation.dto.NoteFolderResponse;
import com.mike.workstation.service.NoteFolderService;

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

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/note-folders")
public class NoteFolderController {
    private final NoteFolderService folderService;

    public NoteFolderController(NoteFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<NoteFolderResponse> list(@PathVariable Long workspaceId) {
        return folderService.list(workspaceId);
    }

    @GetMapping("/{id}")
    public NoteFolderResponse get(@PathVariable Long workspaceId, @PathVariable Long id) {
        return folderService.get(workspaceId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteFolderResponse create(
            @PathVariable Long workspaceId, @RequestBody NoteFolderRequest request) {
        return folderService.create(workspaceId, request);
    }

    @PutMapping("/{id}")
    public NoteFolderResponse update(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody NoteFolderRequest request) {
        return folderService.update(workspaceId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long id) {
        folderService.delete(workspaceId, id);
    }
}
