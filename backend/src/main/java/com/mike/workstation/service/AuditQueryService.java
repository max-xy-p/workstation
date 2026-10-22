package com.mike.workstation.service;

import com.mike.workstation.assembler.AuditResponseAssembler;
import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.AuditEventResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.mapper.AuditMapper;

import org.springframework.stereotype.Service;

/** 经 Workspace Permission 保护的审计查询。 */
@Service
public class AuditQueryService {
    private final AuditMapper auditMapper;
    private final WorkspaceAuthorizationService authorization;

    public AuditQueryService(AuditMapper auditMapper, WorkspaceAuthorizationService authorization) {
        this.auditMapper = auditMapper;
        this.authorization = authorization;
    }

    public PageResponse<AuditEventResponse> list(
            Long workspaceId,
            String actorSubject,
            String action,
            String resourceType,
            AuditResult result,
            int page,
            int size) {
        authorization.require(workspaceId, WorkspacePermission.AUDIT_READ);
        PageQuery query = PageQuery.of(page, size);
        var items =
                auditMapper
                        .listPage(
                                workspaceId,
                                actorSubject,
                                action,
                                resourceType,
                                result,
                                query.offset(),
                                query.size())
                        .stream()
                        .map(AuditResponseAssembler::toResponse)
                        .toList();
        long total = auditMapper.count(workspaceId, actorSubject, action, resourceType, result);
        return PageResponse.of(items, page, size, total);
    }
}
