package com.mike.workstation.security;

import com.mike.workstation.domain.PrincipalType;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
/** 集中解析 JWT，业务 Controller 和 Service 不直接读取 Claim。 */
public class CurrentPrincipalResolver {
    public CurrentPrincipal require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            throw new IllegalStateException("当前请求缺少已验证的 JWT 主体");
        }

        String subjectId = token.getToken().getSubject();
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalStateException("访问令牌缺少 sub");
        }
        Set<String> roles = token.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
        PrincipalType type = roles.contains("WORKSTATION_SERVICE") ? PrincipalType.SERVICE : PrincipalType.USER;
        String username = token.getToken().getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = token.getName();
        }
        return new CurrentPrincipal(
                subjectId,
                type,
                username,
                token.getToken().getClaimAsString("name"),
                roles
        );
    }
}
