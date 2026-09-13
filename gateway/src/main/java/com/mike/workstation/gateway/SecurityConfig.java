package com.mike.workstation.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {
    /**
     * Gateway 面向浏览器，因此登录 Session 和 CSRF 都在这一层处理。
     * Backend 不读取这个 Session；Gateway 转发请求时会把 Access Token 放进 Authorization 请求头。
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clients,
            ServerLogoutSuccessHandler keycloakLogoutSuccessHandler) {
        // 前端需要读取 CSRF Cookie，并把同一个值放进写请求 Header；这里不存放登录凭据。
        var csrfRepository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");

        return http
                .authorizeExchange(authorize -> authorize
                        // 健康检查、获取 CSRF Token、OIDC 发起入口和回调必须允许匿名访问。
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/auth/csrf").permitAll()
                        .pathMatchers("/oauth2/**", "/login/**").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationRequestResolver(authorizationRequestResolver(clients))
                        .authenticationSuccessHandler(
                                new RedirectServerAuthenticationSuccessHandler("/login?login=success")))
                .oauth2Client(client -> { })
                .logout(logout -> logout
                        // 退出必须使用 POST 并通过 CSRF 校验，避免第三方页面强制用户退出。
                        .requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/auth/logout"))
                        .logoutSuccessHandler(keycloakLogoutSuccessHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ignored) -> writeError(
                                exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"))
                        .accessDeniedHandler((exchange, ignored) -> writeError(
                                exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "请求校验失败或权限不足")))
                .requestCache(cache -> cache.disable())
                .build();
    }

    @Bean
    public ServerLogoutSuccessHandler keycloakLogoutSuccessHandler(
            GatewaySsoProperties properties) {
        return (exchange, authentication) -> {
            // id_token_hint 让 Keycloak 确认要结束哪一个 SSO 会话。
            String idToken = authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser
                    ? oidcUser.getIdToken().getTokenValue()
                    : null;
            var uriBuilder = org.springframework.web.util.UriComponentsBuilder
                    .fromUriString(properties.publicKeycloakBaseUrl())
                    .path("/realms/workstation/protocol/openid-connect/logout")
                    .queryParam("client_id", "workstation-gateway")
                    .queryParam("post_logout_redirect_uri", properties.applicationBaseUrl() + "/login?logout=success");
            if (idToken != null) {
                uriBuilder.queryParam("id_token_hint", idToken);
            }

            // Gateway 本地 Session 由 Spring Security 清理；随后让浏览器跳转到 Keycloak 完成全局退出。
            URI location = uriBuilder.build().encode().toUri();
            exchange.getExchange().getResponse().setStatusCode(HttpStatus.SEE_OTHER);
            exchange.getExchange().getResponse().getHeaders().setLocation(location);
            return exchange.getExchange().getResponse().setComplete();
        };
    }

    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository(
            GatewaySsoProperties properties) {
        // 浏览器必须看到公网地址；Gateway 与 Keycloak 的服务端通信则走 Docker 内网地址。
        // issuer 仍使用公网地址，确保 ID Token 中的 iss 声明得到严格校验。
        String realmBaseUrl = properties.internalRealmBaseUrl();
        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientName("Keycloak")
                .clientId("workstation-gateway")
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(properties.issuerUri())
                .authorizationUri(properties.issuerUri() + "/protocol/openid-connect/auth")
                .tokenUri(realmBaseUrl + "/protocol/openid-connect/token")
                .userInfoUri(realmBaseUrl + "/protocol/openid-connect/userinfo")
                .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
                .userNameAttributeName("preferred_username")
                .jwkSetUri(realmBaseUrl + "/protocol/openid-connect/certs")
                .providerConfigurationMetadata(Map.of(
                        "issuer", properties.issuerUri(),
                        "end_session_endpoint", properties.issuerUri() + "/protocol/openid-connect/logout"))
                .build();
        return new InMemoryReactiveClientRegistrationRepository(keycloak);
    }

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            List<GrantedAuthority> mapped = new ArrayList<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (!(authority instanceof OidcUserAuthority oidcAuthority)) {
                    continue;
                }
                var realmAccess = oidcAuthority.getIdToken().getClaimAsMap("realm_access");
                if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                    continue;
                }
                // Keycloak Realm 角色位于 realm_access.roles；Spring Security 使用 ROLE_ 前缀判断角色。
                roles.stream()
                        .map(String::valueOf)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(mapped::add);
            }
            return mapped;
        };
    }

    private DefaultServerOAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ReactiveClientRegistrationRepository clients) {
        var resolver = new DefaultServerOAuth2AuthorizationRequestResolver(clients);
        // PKCE 将授权请求与回调绑定，截获授权码的一方无法单独兑换 Token。
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    private Mono<Void> writeError(
            org.springframework.web.server.ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
