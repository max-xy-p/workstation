package com.mike.workstation.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTests {
    private final AuthController controller = new AuthController();

    @Test
    void exposesCsrfTokenForBrowserClient() {
        CsrfToken token = new CsrfToken() {
            @Override
            public String getHeaderName() {
                return "X-XSRF-TOKEN";
            }

            @Override
            public String getParameterName() {
                return "_csrf";
            }

            @Override
            public String getToken() {
                return "token-value";
            }
        };

        // 通过真实 WebFlux 参数解析调用，防止把 CsrfToken 接口误当作模型对象实例化。
        WebTestClient.bindToController(controller)
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(token));
                    return chain.filter(exchange);
                })
                .build()
                .get().uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.headerName").isEqualTo("X-XSRF-TOKEN")
                .jsonPath("$.parameterName").isEqualTo("_csrf")
                .jsonPath("$.token").isEqualTo("token-value");
    }

    @Test
    void reportsSystemRolesFromGatewaySecurityContext() {
        var authentication = new TestingAuthenticationToken(
                "owner", null, "ROLE_WORKSTATION_USER", "ROLE_WORKSTATION_ADMIN");

        AuthController.CurrentUserResponse response = controller.me(authentication);

        assertThat(response.subjectId()).isEqualTo("owner");
        assertThat(response.username()).isEqualTo("owner");
        assertThat(response.principalType()).isEqualTo("USER");
        assertThat(response.systemRoles()).containsExactly("WORKSTATION_ADMIN", "WORKSTATION_USER");
    }

    @Test
    void logoutEndsKeycloakSessionAndReturnsToApplication() {
        var properties = new GatewaySsoProperties(
                "https://example.test",
                "https://example.test/auth",
                "http://keycloak:8080/auth",
                "test-client-secret",
                "workstation-api");
        var handler = new SecurityConfig().keycloakLogoutSuccessHandler(properties);
        var idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("sub", "owner", "preferred_username", "owner"));
        var principal = new DefaultOidcUser(List.of(), idToken, "preferred_username");
        var authentication = new TestingAuthenticationToken(principal, null);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/logout").build());
        var filterExchange = new WebFilterExchange(exchange, ignored -> Mono.empty());

        handler.onLogoutSuccess(filterExchange, authentication).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(exchange.getResponse().getHeaders().getLocation())
                .hasToString("https://example.test/auth/realms/workstation/protocol/openid-connect/logout"
                        + "?client_id=workstation-gateway"
                        + "&post_logout_redirect_uri=https://example.test/login?logout%3Dsuccess"
                        + "&id_token_hint=id-token-value");
    }

    @Test
    void logoutWithoutOidcPrincipalDoesNotExposeIdTokenHint() {
        var properties = new GatewaySsoProperties(
                "https://example.test", "https://example.test/auth",
                "http://keycloak:8080/auth", "test-client-secret", "workstation-api");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/logout").build());
        var filterExchange = new WebFilterExchange(exchange, ignored -> Mono.empty());

        new SecurityConfig().keycloakLogoutSuccessHandler(properties)
                .onLogoutSuccess(filterExchange, null).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(exchange.getResponse().getHeaders().getLocation().toString())
                .contains("client_id=workstation-gateway")
                .doesNotContain("id_token_hint");
    }

    @Test
    void oidcClientValidatesPublicIssuerButUsesInternalBackChannel() {
        var properties = new GatewaySsoProperties(
                "https://workstation.example.test",
                "https://workstation.example.test/auth",
                "http://keycloak:8080/auth",
                "test-client-secret",
                "workstation-api");

        var repository = new SecurityConfig().clientRegistrationRepository(properties);
        var registration = repository.findByRegistrationId("keycloak").block();

        assertThat(registration).isNotNull();
        assertThat(registration.getProviderDetails().getIssuerUri())
                .isEqualTo("https://workstation.example.test/auth/realms/workstation");
        assertThat(registration.getProviderDetails().getAuthorizationUri())
                .startsWith("https://workstation.example.test/auth/");
        assertThat(registration.getProviderDetails().getTokenUri())
                .startsWith("http://keycloak:8080/auth/");
        assertThat(registration.getProviderDetails().getJwkSetUri())
                .startsWith("http://keycloak:8080/auth/");
    }
}
