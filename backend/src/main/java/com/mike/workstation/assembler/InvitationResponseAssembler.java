package com.mike.workstation.assembler;

import com.mike.workstation.domain.WorkspaceInvitation;
import com.mike.workstation.dto.InvitationResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 过期状态按当前期限计算，不依赖后台清理是否及时。 */
public final class InvitationResponseAssembler {
    private InvitationResponseAssembler() {}

    public static InvitationResponse toResponse(WorkspaceInvitation invitation) {
        String status = invitation.getStatus();
        if ("PENDING".equals(status)
                && !invitation.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.systemDefault()))) {
            status = "EXPIRED";
        }
        return new InvitationResponse(invitation.getId(), invitation.getWorkspaceId(),
                invitation.getWorkspaceName(), invitation.getEmail(), invitation.getRole(),
                status, invitation.getMailStatus(), ApiTime.toOffset(invitation.getCreatedAt()),
                ApiTime.toOffset(invitation.getExpiresAt()));
    }
}
