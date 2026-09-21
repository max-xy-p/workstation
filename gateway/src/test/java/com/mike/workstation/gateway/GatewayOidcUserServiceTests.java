package com.mike.workstation.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayOidcUserServiceTests {
    @Test
    void grantsBrowserRolesFromValidatedAccessToken() {
        var request = userRequest(Map.of("sub", "owner", "preferred_username", "owner"));
        var service = new GatewayOidcUserService(
                token -> Mono.just(accessToken(Map.of("roles", List.of(
                        "WORKSTATION_USER", "WORKSTATION_ADMIN", "WORKSTATION_SERVICE")))),
                ignored -> Mono.just(oidcUser(request)));

        OidcUser user = service.loadUser(request).block();

        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("owner");
        assertThat(user.getAuthorities()).extracting("authority")
                .contains("ROLE_WORKSTATION_USER", "ROLE_WORKSTATION_ADMIN")
                .doesNotContain("ROLE_WORKSTATION_SERVICE");
    }

    @Test
    void doesNotReadRolesFromIdTokenWhenAccessTokenHasNone() {
        var request = userRequest(Map.of(
                "sub", "owner", "preferred_username", "owner",
                "realm_access", Map.of("roles", List.of("WORKSTATION_ADMIN"))));
        var service = new GatewayOidcUserService(
                token -> Mono.just(accessToken(Map.of())),
                ignored -> Mono.just(oidcUser(request)));

        OidcUser user = service.loadUser(request).block();

        assertThat(user).isNotNull();
        assertThat(user.getAuthorities()).extracting("authority")
                .doesNotContain("ROLE_WORKSTATION_ADMIN", "ROLE_WORKSTATION_USER");
    }

    @Test
    void rejectsInvalidAccessTokenBeforeLoadingOidcUser() {
        var request = userRequest(Map.of("sub", "owner", "preferred_username", "owner"));
        var delegateCalled = new AtomicBoolean();
        ReactiveJwtDecoder decoder = token -> Mono.error(new BadJwtException("invalid token"));
        var service = new GatewayOidcUserService(decoder, ignored -> {
            delegateCalled.set(true);
            return Mono.just(oidcUser(request));
        });

        assertThatThrownBy(() -> service.loadUser(request).block()).isInstanceOf(BadJwtException.class);
        assertThat(delegateCalled).isFalse();
    }

    private OidcUserRequest userRequest(Map<String, Object> idTokenClaims) {
        var properties = new GatewaySsoProperties(
                "https://example.test", "https://example.test/auth",
                "http://keycloak:8080/auth", "test-client-secret", "workstation-api");
        var registration = new SecurityConfig().clientRegistrationRepository(properties)
                .findByRegistrationId("keycloak").block();
        Instant now = Instant.now();
        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60), Set.of("openid"));
        var idToken = new OidcIdToken("id-token", now, now.plusSeconds(60), idTokenClaims);
        return new OidcUserRequest(registration, accessToken, idToken);
    }

    private OidcUser oidcUser(OidcUserRequest request) {
        return new DefaultOidcUser(List.of(), request.getIdToken(), "preferred_username");
    }

    private Jwt accessToken(Map<String, Object> realmAccess) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("owner")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if (!realmAccess.isEmpty()) {
            builder.claim("realm_access", realmAccess);
        }
        return builder.build();
    }
}
