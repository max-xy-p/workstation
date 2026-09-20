package com.mike.workstation.security;

import com.mike.workstation.domain.PrincipalType;
import java.util.Set;

/** 从已经验证的 JWT 得到的当前调用主体。 */
public record CurrentPrincipal(
        String subjectId,
        PrincipalType principalType,
        String username,
        String displayName,
        Set<String> systemRoles
) {
}
