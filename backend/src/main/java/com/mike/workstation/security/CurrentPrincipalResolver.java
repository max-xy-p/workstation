package com.mike.workstation.security;

import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.time.Instant;
import java.util.stream.Collectors;

/** 集中解析 JWT，业务 Controller 和 Service 不直接读取 Claim。 */
@Component
public class CurrentPrincipalResolver {
    /** SSE 每次连接的寿命不能超过当前已验证访问令牌的到期时间。 */
    public Instant requireExpiresAt() {
        require();
        var authentication = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return authentication.getToken().getExpiresAt();
    }

    /** 只信任已经验证的访问令牌中的邮箱声明，机器主体不能接受邀请。 */
    public String requireVerifiedEmail() {
        CurrentPrincipal principal = require();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (principal.principalType() != PrincipalType.USER
                || !(authentication instanceof JwtAuthenticationToken token)
                || !Boolean.TRUE.equals(token.getToken().getClaimAsBoolean("email_verified"))) {
            throw new WorkspaceAccessDeniedException("请先验证受邀邮箱");
        }
        String email = token.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new WorkspaceAccessDeniedException("访问令牌缺少受邀邮箱");
        }
        return email;
    }

    public CurrentPrincipal require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()) {
            throw new IllegalStateException("当前请求缺少已验证的 JWT 主体");
        }

        String subjectId = token.getToken().getSubject();
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalStateException("访问令牌缺少 sub");
        }
        Set<String> roles =
                token.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring("ROLE_".length()))
                        .collect(Collectors.toUnmodifiableSet());
        PrincipalType type =
                roles.contains("WORKSTATION_SERVICE") ? PrincipalType.SERVICE : PrincipalType.USER;
        String username = token.getToken().getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = token.getName();
        }
        return new CurrentPrincipal(
                subjectId, type, username, token.getToken().getClaimAsString("name"), roles);
    }
}
