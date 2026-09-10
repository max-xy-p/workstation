package com.mike.workstation.controller;

import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.dto.NoteFolderRequest;
import com.mike.workstation.service.NoteFolderService;
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
@RequestMapping("/api/note-folders")
public class NoteFolderController {
    private final NoteFolderService folderService;

    public NoteFolderController(NoteFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<NoteFolder> list() {
        return folderService.list();
    }

    @GetMapping("/{id}")
    public NoteFolder get(@PathVariable Long id) {
        return folderService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteFolder create(@RequestBody NoteFolderRequest request) {
        return folderService.create(request);
    }

    @PutMapping("/{id}")
    public NoteFolder update(@PathVariable Long id, @RequestBody NoteFolderRequest request) {
        return folderService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        folderService.delete(id);
    }
}
