package com.mike.workstation.gateway;

import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/** 注册使用与登录相同的授权码流程，只允许将明确的注册意图转成标准 prompt 参数。 */
final class RegistrationAuthorizationRequestResolver
        implements ServerOAuth2AuthorizationRequestResolver {
    private final DefaultServerOAuth2AuthorizationRequestResolver delegate;

    RegistrationAuthorizationRequestResolver(ReactiveClientRegistrationRepository clients) {
        delegate = new DefaultServerOAuth2AuthorizationRequestResolver(clients);
        delegate.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
        return withRegistrationPrompt(exchange, delegate.resolve(exchange));
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(
            ServerWebExchange exchange, String registrationId) {
        return withRegistrationPrompt(exchange, delegate.resolve(exchange, registrationId));
    }

    private Mono<OAuth2AuthorizationRequest> withRegistrationPrompt(
            ServerWebExchange exchange, Mono<OAuth2AuthorizationRequest> request) {
        if (!"/oauth2/authorization/keycloak".equals(exchange.getRequest().getPath().value())
                || !"true"
                        .equals(exchange.getRequest().getQueryParams().getFirst("registration"))) {
            return request;
        }
        // 从已生成的请求派生，保留随机 state、nonce 与 PKCE，不转发外部传入的跳转或权限参数。
        return request.map(
                value ->
                        OAuth2AuthorizationRequest.from(value)
                                .additionalParameters(
                                        parameters -> parameters.put("prompt", "create"))
                                .build());
    }
}
