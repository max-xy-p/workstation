package com.mike.workstation.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class RegistrationAuthorizationRequestResolverTests {
    private final RegistrationAuthorizationRequestResolver resolver =
            new RegistrationAuthorizationRequestResolver(
                    new InMemoryReactiveClientRegistrationRepository(
                            ClientRegistration.withRegistrationId("keycloak")
                                    .clientId("workstation-gateway")
                                    .clientSecret("test-secret")
                                    .authorizationGrantType(
                                            AuthorizationGrantType.AUTHORIZATION_CODE)
                                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                                    .authorizationUri("https://identity.example.com/authorize")
                                    .tokenUri("https://identity.example.com/token")
                                    .scope("openid", "profile", "email")
                                    .build()));

    private MockServerWebExchange exchange(String query) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(
                                "https://workstation.example.com/oauth2/authorization/keycloak"
                                        + query)
                        .build());
    }

    private void assertBoundRequest(OAuth2AuthorizationRequest request) {
        assertThat(request.getState()).isNotBlank();
        assertThat(request.getAdditionalParameters()).containsKeys("nonce", "code_challenge");
        assertThat(request.getAdditionalParameters())
                .containsEntry("code_challenge_method", "S256");
        assertThat(request.getAttributes()).containsKey("code_verifier");
        assertThat(request.getRedirectUri())
                .isEqualTo("https://workstation.example.com/login/oauth2/code/keycloak");
    }

    @Test
    void registrationAddsPromptAndKeepsOidcAndPkceBinding() {
        var request = resolver.resolve(exchange("?registration=true")).block();
        assertBoundRequest(request);
        assertThat(request.getAdditionalParameters()).containsEntry("prompt", "create");
        var next = resolver.resolve(exchange("?registration=true")).block();
        assertThat(next.getState()).isNotEqualTo(request.getState());
        assertThat(next.getAttributes().get("code_verifier"))
                .isNotEqualTo(request.getAttributes().get("code_verifier"));
    }

    @Test
    void ordinaryLoginDoesNotAcquireRegistrationPrompt() {
        for (String query : new String[] {"", "?registration=false", "?registration=TRUE"}) {
            var request = resolver.resolve(exchange(query)).block();
            assertBoundRequest(request);
            assertThat(request.getAdditionalParameters()).doesNotContainKey("prompt");
        }
    }

    @Test
    void explicitResolutionDoesNotForwardUntrustedRedirectOrScopes() {
        var request =
                resolver.resolve(
                                exchange(
                                        "?registration=true&redirect_uri=https://evil.example.com&scope=admin&prompt=none"),
                                "keycloak")
                        .block();
        assertBoundRequest(request);
        assertThat(request.getAdditionalParameters()).containsEntry("prompt", "create");
        assertThat(request.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
    }
}
