package com.mike.workstation.controller;

import com.mike.workstation.dto.WorkspaceMemberRequest;
import com.mike.workstation.dto.WorkspaceMemberCandidate;
import com.mike.workstation.dto.WorkspaceMemberResponse;
import com.mike.workstation.dto.WorkspaceMemberRoleRequest;
import com.mike.workstation.dto.WorkspaceRequest;
import com.mike.workstation.dto.WorkspaceResponse;
import com.mike.workstation.service.WorkspaceService;
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
@RequestMapping("/api/workspaces")
/** 当前主体的工作空间和成员管理接口。 */
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        return workspaceService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@RequestBody WorkspaceRequest request) {
        return workspaceService.createShared(request);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse get(@PathVariable Long workspaceId) {
        return workspaceService.get(workspaceId);
    }

    @PutMapping("/{workspaceId}")
    public WorkspaceResponse update(@PathVariable Long workspaceId, @RequestBody WorkspaceRequest request) {
        return workspaceService.update(workspaceId, request);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> listMembers(@PathVariable Long workspaceId) {
        return workspaceService.listMembers(workspaceId);
    }

    @GetMapping("/{workspaceId}/member-candidates")
    public List<WorkspaceMemberCandidate> findMemberCandidates(
            @PathVariable Long workspaceId, @RequestParam String username
    ) {
        return workspaceService.findMemberCandidates(workspaceId, username);
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse addMember(
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceMemberRequest request
    ) {
        return workspaceService.addMember(workspaceId, request);
    }

    @PutMapping("/{workspaceId}/members/{subjectId}")
    public WorkspaceMemberResponse updateMember(
            @PathVariable Long workspaceId,
            @PathVariable String subjectId,
            @RequestBody WorkspaceMemberRoleRequest request
    ) {
        return workspaceService.updateMemberRole(workspaceId, subjectId, request == null ? null : request.role());
    }

    @DeleteMapping("/{workspaceId}/members/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long workspaceId, @PathVariable String subjectId) {
        workspaceService.removeMember(workspaceId, subjectId);
    }
}
