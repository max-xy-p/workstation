package com.mike.workstation.controller;

import com.mike.workstation.dto.InvitationRequest;
import com.mike.workstation.dto.InvitationTokenRequest;
import com.mike.workstation.dto.InvitationResponse;
import com.mike.workstation.dto.WorkspaceResponse;
import com.mike.workstation.service.InvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 管理接口按空间授权，预览与接受按已验证邮箱和凭据授权。 */
@RestController
@RequestMapping("/api")
public class InvitationController {
    private final InvitationService invitations;
    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    public List<InvitationResponse> list(@PathVariable Long workspaceId) {
        return invitations.list(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@PathVariable Long workspaceId, @RequestBody InvitationRequest request) {
        return invitations.create(workspaceId, request);
    }

    @PostMapping("/workspaces/{workspaceId}/invitations/{invitationId}/revoke")
    public InvitationResponse revoke(@PathVariable Long workspaceId, @PathVariable String invitationId) {
        return invitations.revoke(workspaceId, invitationId);
    }

    @PostMapping("/invitations/preview")
    public InvitationResponse preview(@RequestBody InvitationTokenRequest request) {
        return invitations.preview(request);
    }

    @PostMapping("/invitations/accept")
    public WorkspaceResponse accept(@RequestBody InvitationTokenRequest request) {
        return invitations.accept(request);
    }
}
