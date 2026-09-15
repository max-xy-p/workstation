package com.mike.workstation.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workstation.sso")
// 集中保存 SSO 地址和 Client Secret，避免安全配置类直接散落读取环境变量。
public record GatewaySsoProperties(
        String applicationBaseUrl,
        String publicKeycloakBaseUrl,
        String internalKeycloakBaseUrl,
        String clientSecret,
        String audience) {
    public GatewaySsoProperties {
        applicationBaseUrl = stripTrailingSlash(applicationBaseUrl);
        publicKeycloakBaseUrl = stripTrailingSlash(publicKeycloakBaseUrl);
        internalKeycloakBaseUrl = stripTrailingSlash(internalKeycloakBaseUrl);
        requireValue(applicationBaseUrl, "WORKSTATION_PUBLIC_URL");
        requireValue(publicKeycloakBaseUrl, "KEYCLOAK_PUBLIC_BASE_URL");
        requireValue(internalKeycloakBaseUrl, "KEYCLOAK_INTERNAL_BASE_URL");
        requireValue(clientSecret, "KEYCLOAK_CLIENT_SECRET");
        requireValue(audience, "OAUTH2_AUDIENCE");
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static void requireValue(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(environmentName + " must not be blank");
        }
    }

    public String issuerUri() {
        // Token 的 iss 声明是浏览器可见的公开 Realm 地址。
        return publicKeycloakBaseUrl + "/realms/workstation";
    }

    public String internalRealmBaseUrl() {
        // Token、UserInfo 和 JWK 请求不经过公网代理，直接走容器网络。
        return internalKeycloakBaseUrl + "/realms/workstation";
    }
}
