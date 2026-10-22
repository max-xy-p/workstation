package com.mike.workstation.gateway;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/** 浏览器登录时只从已验证的 Access Token 提取系统准入角色。 */
final class GatewayOidcUserService implements ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {
    private final ReactiveJwtDecoder jwtDecoder;
    private final ReactiveOAuth2UserService<OidcUserRequest, OidcUser> delegate;

    GatewayOidcUserService(ReactiveJwtDecoder jwtDecoder) {
        this(jwtDecoder, new OidcReactiveOAuth2UserService());
    }

    GatewayOidcUserService(
            ReactiveJwtDecoder jwtDecoder,
            ReactiveOAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.jwtDecoder = jwtDecoder;
        this.delegate = delegate;
    }

    @Override
    public Mono<OidcUser> loadUser(OidcUserRequest request) {
        // 复用 Gateway 的签名、Issuer、Audience 和有效期校验；失败时不创建登录会话。
        return jwtDecoder
                .decode(request.getAccessToken().getTokenValue())
                .flatMap(
                        accessToken ->
                                delegate.loadUser(request)
                                        .map(user -> withSystemRoles(request, user, accessToken)));
    }

    private OidcUser withSystemRoles(OidcUserRequest request, OidcUser user, Jwt accessToken) {
        var authorities = new ArrayList<GrantedAuthority>(user.getAuthorities());
        Map<String, Object> realmAccess = accessToken.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                if ("WORKSTATION_USER".equals(role) || "WORKSTATION_ADMIN".equals(role)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }
        String userNameAttributeName =
                request.getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName();
        // 保留原 OIDC 身份与 UserInfo，只有权限来源改为 Access Token。
        return StringUtils.hasText(userNameAttributeName)
                ? new DefaultOidcUser(
                        authorities, user.getIdToken(), user.getUserInfo(), userNameAttributeName)
                : new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
    }
}
