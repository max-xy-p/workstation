package com.mike.workstation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mike.workstation.domain.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentPrincipalResolverTests {
    private final CurrentPrincipalResolver resolver = new CurrentPrincipalResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceRoleAndClaimsBecomeCurrentPrincipal() {
        authenticate(Map.of("sub", "service-1", "preferred_username", "worker", "name", "任务服务"),
                "ROLE_WORKSTATION_SERVICE", "SCOPE_read");

        var principal = resolver.require();

        assertThat(principal.subjectId()).isEqualTo("service-1");
        assertThat(principal.principalType()).isEqualTo(PrincipalType.SERVICE);
        assertThat(principal.username()).isEqualTo("worker");
        assertThat(principal.displayName()).isEqualTo("任务服务");
        assertThat(principal.systemRoles()).containsExactly("WORKSTATION_SERVICE");
    }

    @Test
    void userWithoutPreferredUsernameFallsBackToTokenName() {
        authenticate(Map.of("sub", "user-1"), "ROLE_WORKSTATION_USER");

        var principal = resolver.require();

        assertThat(principal.principalType()).isEqualTo(PrincipalType.USER);
        assertThat(principal.username()).isEqualTo("user-1");
    }

    @Test
    void rejectsMissingAuthenticationAndMissingSubject() {
        assertThatThrownBy(resolver::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已验证的 JWT");

        authenticate(Map.of("preferred_username", "worker"), "ROLE_WORKSTATION_SERVICE");
        assertThatThrownBy(resolver::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 sub");
    }

    private void authenticate(Map<String, Object> claims, String... authorities) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
        var roles = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = new JwtAuthenticationToken(jwt, roles);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
