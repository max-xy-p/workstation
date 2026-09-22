package com.mike.workstation.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // 前端先获取该值，之后的 POST/PUT/PATCH/DELETE 请求必须放入响应指定的 Header。
    @GetMapping("/csrf")
    public Mono<CsrfResponse> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return Mono.error(new IllegalStateException("CSRF Token 未初始化"));
        }
        return token.map(value -> new CsrfResponse(
                value.getHeaderName(),
                value.getParameterName(),
                value.getToken()))
                .switchIfEmpty(Mono.error(new IllegalStateException("CSRF Token 未初始化")));
    }

    // 浏览器用 Gateway Session 查询登录用户，不会获得 Access Token 或 Refresh Token。
    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String subjectId = authentication.getName();
        String username = authentication.getName();
        String displayName = null;
        if (authentication.getPrincipal() instanceof OidcUser user) {
            subjectId = user.getSubject();
            username = user.getPreferredUsername();
            displayName = user.getFullName();
        }
        List<String> systemRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_WORKSTATION_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new CurrentUserResponse(subjectId, username, displayName, "USER", systemRoles);
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record CurrentUserResponse(
            String subjectId,
            String username,
            String displayName,
            String principalType,
            List<String> systemRoles
    ) {
    }
}
