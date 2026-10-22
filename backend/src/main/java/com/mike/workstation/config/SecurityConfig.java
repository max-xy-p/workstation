package com.mike.workstation.config;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Configuration
public class SecurityConfig {
    /** Webhook 专用入口使用原文签名认证，不接受 Cookie 或 Bearer 代替签名。 */
    @Bean
    @Order(0)
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/hooks/github/*")
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(HttpMethod.POST, "/api/hooks/github/*")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .requestCache(c -> c.disable())
                .build();
    }

    /**
     * Backend 是 OAuth2 Resource Server，只信任 Keycloak 签发的 Bearer Token。 它不负责登录和浏览器 Session，但仍必须独立校验
     * Token，防止请求绕过 Gateway。
     */
    @Bean
    @Order(10)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers("/api/**")
                                        .hasAnyRole(
                                                "WORKSTATION_USER",
                                                "WORKSTATION_ADMIN",
                                                "WORKSTATION_SERVICE")
                                        .anyRequest()
                                        .permitAll())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Backend 不使用 Cookie 认证，因此没有浏览器 CSRF 状态；CSRF 已由 Gateway 校验。
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter()))
                                        .authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        writeError(
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "UNAUTHENTICATED",
                                                                "访问令牌无效或已过期"))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        writeError(
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                "ACCESS_DENIED",
                                                                "权限不足")))
                .requestCache(cache -> cache.disable());

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(ResourceServerProperties properties) {
        // JWK 地址用于取得 Keycloak 公钥并验证 JWT 签名，不包含任何私钥。
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        // 除签名和有效期外，同时限制签发方和目标 API，避免接受其他 Realm 或其他 Client 的 Token。
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<Collection<String>>(
                        "aud",
                        audience -> audience != null && audience.contains(properties.audience()));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    var authorities = new ArrayList<GrantedAuthority>();
                    scopes.convert(jwt)
                            .forEach(
                                    authority ->
                                            authorities.add(
                                                    new SimpleGrantedAuthority(
                                                            authority.getAuthority())));

                    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                    if (realmAccess != null
                            && realmAccess.get("roles") instanceof Collection<?> roles) {
                        // 把 Keycloak Realm 角色转换为 Spring Security 能识别的 ROLE_* 系统角色。
                        roles.stream()
                                .map(String::valueOf)
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .forEach(authorities::add);
                    }
                    return authorities;
                });
        return converter;
    }

    private static void writeError(
            HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .printf(
                        "{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                        code, message, OffsetDateTime.now(ZoneId.systemDefault()));
    }
}
