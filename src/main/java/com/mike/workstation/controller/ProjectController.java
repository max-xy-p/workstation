package com.mike.workstation.controller;

import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.dto.ProjectDetailResponse;
import com.mike.workstation.dto.ProjectRequest;
import com.mike.workstation.dto.ProjectStatusRequest;
import com.mike.workstation.service.ProjectService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<Project> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status
    ) {
        return projectService.list(keyword, status);
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse detail(@PathVariable Long id) {
        return projectService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable Long id, @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public Project updateStatus(@PathVariable Long id, @RequestBody ProjectStatusRequest request) {
        return projectService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }
}
