package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** Keycloak 主体在业务库中的最小投影。 */
@Data
public class SecurityPrincipal {
    private String subjectId;
    private PrincipalType principalType;
    private String username;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
