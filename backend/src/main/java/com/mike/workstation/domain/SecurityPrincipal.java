package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
/** Keycloak 主体在业务库中的最小投影。 */
public class SecurityPrincipal {
    private String subjectId;
    private PrincipalType principalType;
    private String username;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
