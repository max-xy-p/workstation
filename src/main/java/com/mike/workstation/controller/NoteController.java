package com.mike.workstation.controller;

import com.mike.workstation.domain.Note;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.dto.NoteImportResponse;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.service.NoteImportService;
import com.mike.workstation.service.NoteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;
    private final NoteImportService noteImportService;

    public NoteController(NoteService noteService, NoteImportService noteImportService) {
        this.noteService = noteService;
        this.noteImportService = noteImportService;
    }

    @GetMapping
    public List<Note> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long projectId
    ) {
        return noteService.list(keyword, categoryId, tagId, projectId);
    }

    @GetMapping("/{id}")
    public Note get(@PathVariable Long id) {
        return noteService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Note create(@RequestBody NoteRequest request) {
        return noteService.create(request);
    }

    @PostMapping("/import")
    public NoteImportResponse importMarkdown(@RequestBody NoteImportRequest request) {
        return noteImportService.importMarkdown(request);
    }

    @PutMapping("/{id}")
    public Note update(@PathVariable Long id, @RequestBody NoteRequest request) {
        return noteService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        noteService.delete(id);
    }
}
