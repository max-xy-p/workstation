package com.mike.workstation.controller;

import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.dto.AuditEventResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.service.AuditQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/audit-events")
/** 工作空间审计事件查询接口。 */
public class AuditController {
    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String actorSubject,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) AuditResult result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return auditQueryService.list(workspaceId, actorSubject, action, resourceType, result, page, size);
    }
}
