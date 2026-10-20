package com.mike.workstation.controller;

import com.mike.workstation.dto.*;
import com.mike.workstation.service.TaskIssueService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 任务来源业务接口，不触发平台写入或同步。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class TaskIssueController {
    private final TaskIssueService service;
    public TaskIssueController(TaskIssueService service) { this.service=service; }
    @GetMapping("/issue-sources")
    public PageResponse<TaskIssueSourceResponse> list(@PathVariable Long workspaceId,
            @RequestParam(required=false) Long projectId,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.list(workspaceId,projectId,page,size);
    }
    @PostMapping("/projects/{id}/issue-tasks")
    public ResponseEntity<IssueTaskResult> create(@PathVariable Long workspaceId,@PathVariable Long id,
            @RequestBody IssueTaskCreateRequest request) {
        IssueTaskResult result=service.create(workspaceId,id,request);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result);
    }
    @PostMapping("/projects/{id}/issue-links")
    public IssueTaskResult link(@PathVariable Long workspaceId,@PathVariable Long id,@RequestBody IssueTaskLinkRequest request) {
        return service.link(workspaceId,id,request);
    }
    @DeleteMapping("/tasks/{id}/issue-source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long workspaceId,@PathVariable Long id) { service.unlink(workspaceId,id); }
}
