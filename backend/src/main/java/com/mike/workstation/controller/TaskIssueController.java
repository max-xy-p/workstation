package com.mike.workstation.controller;

import com.mike.workstation.dto.IssueSourceConfirmRequest;
import com.mike.workstation.dto.IssueTaskCreateRequest;
import com.mike.workstation.dto.IssueTaskLinkRequest;
import com.mike.workstation.dto.IssueTaskResult;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.dto.TaskIssueSourceResponse;
import com.mike.workstation.service.TaskIssueService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 任务来源业务接口，不触发平台写入或同步。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class TaskIssueController {
    private final TaskIssueService service;

    public TaskIssueController(TaskIssueService service) {
        this.service = service;
    }

    @GetMapping("/issue-sources")
    public PageResponse<TaskIssueSourceResponse> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(workspaceId, projectId, page, size);
    }

    @PostMapping("/projects/{id}/issue-tasks")
    public ResponseEntity<IssueTaskResult> create(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody IssueTaskCreateRequest request) {
        IssueTaskResult result = service.create(workspaceId, id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result);
    }

    @PostMapping("/projects/{id}/issue-links")
    public IssueTaskResult link(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody IssueTaskLinkRequest request) {
        return service.link(workspaceId, id, request);
    }

    @PostMapping("/tasks/{id}/issue-source/confirm")
    public TaskIssueSourceResponse confirm(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody IssueSourceConfirmRequest request) {
        return service.confirm(workspaceId, id, request);
    }

    @DeleteMapping("/tasks/{id}/issue-source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long workspaceId, @PathVariable Long id) {
        service.unlink(workspaceId, id);
    }
}
