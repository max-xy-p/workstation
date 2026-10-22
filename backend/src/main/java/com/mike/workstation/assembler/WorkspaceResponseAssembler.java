package com.mike.workstation.assembler;

import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.WorkspaceMemberResponse;
import com.mike.workstation.dto.WorkspaceResponse;

/** 工作空间领域对象到 API Response 的转换。 */
public final class WorkspaceResponseAssembler {
    private WorkspaceResponseAssembler() {}

    public static WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getType(),
                workspace.getRole(),
                WorkspacePermission.forRole(workspace.getRole()).stream().toList(),
                ApiTime.toOffset(workspace.getCreatedAt()),
                ApiTime.toOffset(workspace.getUpdatedAt()));
    }

    public static WorkspaceMemberResponse toMemberResponse(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.getSubjectId(),
                member.getPrincipalType(),
                member.getUsername(),
                member.getDisplayName(),
                member.getRole(),
                ApiTime.toOffset(member.getJoinedAt()));
    }
}
