package com.mike.workstation.dto;
import com.mike.workstation.domain.WorkspaceRole;

/** 创建请求标识在相同操作者和空间内唯一。 */
public record InvitationRequest(String requestId, String email, WorkspaceRole role) {}
