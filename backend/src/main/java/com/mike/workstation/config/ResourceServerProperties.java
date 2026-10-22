package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workstation.security.oauth2")
// Backend 只需要知道谁签发 Token、从哪里取公钥，以及 Token 必须面向哪个 API。
public record ResourceServerProperties(String issuerUri, String jwkSetUri, String audience) {
    public ResourceServerProperties {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("KEYCLOAK_ISSUER_URI must not be blank");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("KEYCLOAK_JWK_SET_URI must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("OAUTH2_AUDIENCE must not be blank");
        }
    }
}
