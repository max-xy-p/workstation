package com.mike.workstation.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BearerSecurityTests {
    @Test
    void bearerMatcherSeparatesMachineRequestsFromBrowserRequests() {
        var bearerExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/workspaces/9/projects")
                .header("Authorization", "Bearer token-value")
                .build());
        var browserExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/workspaces/9/projects").build());

        assertThat(SecurityConfig.bearerRequestMatcher().matches(bearerExchange).block().isMatch()).isTrue();
        assertThat(SecurityConfig.bearerRequestMatcher().matches(browserExchange).block().isMatch()).isFalse();
    }

    @Test
    void jwtConverterMapsServiceRealmRole() {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "service-account-workstation-python",
                        "preferred_username", "service-account-workstation-python",
                        "realm_access", Map.of("roles", List.of("WORKSTATION_SERVICE"))));

        var authentication = SecurityConfig.jwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("service-account-workstation-python");
        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_WORKSTATION_SERVICE");
    }

    @Test
    void securityErrorsUseUnifiedApiErrorShape() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/workspaces/9/projects").build());

        new SecurityConfig().writeError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "访问令牌无效或已过期"
        ).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(body).contains("\"message\":\"访问令牌无效或已过期\"");
        assertThat(body).contains("\"timestamp\":");
    }
}
