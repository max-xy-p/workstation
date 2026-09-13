package com.mike.workstation.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // 前端先获取该值，之后的 POST/PUT/PATCH/DELETE 请求必须放入响应指定的 Header。
    @GetMapping("/csrf")
    public Mono<CsrfResponse> csrf(Mono<CsrfToken> token) {
        return token.map(value -> new CsrfResponse(
                value.getHeaderName(),
                value.getParameterName(),
                value.getToken()));
    }

    // 浏览器用 Gateway Session 查询登录用户，不会获得 Access Token 或 Refresh Token。
    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String username = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser user) {
            username = user.getPreferredUsername();
        }
        boolean owner = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        return new CurrentUserResponse(username, owner);
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record CurrentUserResponse(String username, boolean owner) {
    }
}
