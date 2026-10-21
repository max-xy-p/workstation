package com.mike.workstation.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;

class WebhookSecurityTests {
    @Test void exactWebhookPostNeedsNoSessionOrCsrfAndRejectsOtherMethods() {
        var chain=new SecurityConfig().webhookSecurityWebFilterChain(ServerHttpSecurity.http());
        var client=WebTestClient.bindToWebHandler(exchange->{exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);return exchange.getResponse().setComplete();})
                .webFilter(new WebFilterChainProxy(chain)).build();
        client.post().uri("/api/hooks/github/endpoint").header("Authorization","Bearer invalid").bodyValue("{}").exchange().expectStatus().isAccepted();
        client.get().uri("/api/hooks/github/endpoint").exchange().expectStatus().isForbidden();
    }
}
