package com.mike.workstation.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class SecurityChainTests {
    private static final String API_PATH = "/api/workspaces/9/projects";
    private final SecurityConfig security = new SecurityConfig();

    @Test
    void browserSecurityChainRegistersCsrfOidcAndAuthorizationFilters() {
        var properties = properties();
        var registrations = security.clientRegistrationRepository(properties);
        var http = ServerHttpSecurity.http();
        http.oauth2Login(oauth2 -> oauth2.clientRegistrationRepository(registrations));
        http.oauth2Client(oauth2 -> oauth2.clientRegistrationRepository(registrations));
        var chain = security.browserSecurityWebFilterChain(http, registrations,
                security.keycloakLogoutSuccessHandler(properties));
        assertThat(chain.getWebFilters().collectList().block())
                .extracting(filter -> filter.getClass().getSimpleName())
                .contains("CsrfWebFilter", "AuthorizationWebFilter",
                        "OAuth2LoginAuthenticationWebFilter");
    }

    @Test
    void bearerApiRequiresServiceRole() {
        ReactiveJwtDecoder decoder = token -> Mono.just(jwt(List.of("WORKSTATION_USER")));
        var chain = security.bearerSecurityWebFilterChain(ServerHttpSecurity.http(), decoder);
        var client = client(new WebFilterChainProxy(chain));

        client.get().uri(API_PATH).header("Authorization", "Bearer valid-token")
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void serviceBearerCanWriteWithoutBrowserCsrfToken() {
        ReactiveJwtDecoder decoder = token -> Mono.just(jwt(List.of("WORKSTATION_SERVICE")));
        var chain = security.bearerSecurityWebFilterChain(ServerHttpSecurity.http(), decoder);
        var client = client(new WebFilterChainProxy(chain));

        client.post().uri(API_PATH).header("Authorization", "Bearer valid-token")
                .exchange().expectStatus().isOk();
    }

    private WebTestClient client(WebFilterChainProxy proxy) {
        return WebTestClient.bindToWebHandler(exchange -> {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        }).webFilter(proxy).build();
    }

    private GatewaySsoProperties properties() {
        return new GatewaySsoProperties("https://example.test", "https://example.test/auth",
                "http://keycloak:8080/auth", "test-client-secret", "workstation-api");
    }

    private Jwt jwt(List<String> roles) {
        return new Jwt("valid-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "subject", "realm_access", Map.of("roles", roles)));
    }
}
