package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Base64;

/** 邀请开关、公开入口及独立签名密钥，关闭时不要求配置凭据。 */
@ConfigurationProperties(prefix = "workstation.invitations")
public record InvitationProperties(boolean enabled, String publicUrl, String signingKey,
        String mailUrl, String mailToken) {
    public InvitationProperties {
        if (enabled) {
            URI publicUri = URI.create(publicUrl);
            URI mailUri = URI.create(mailUrl);
            if (!validHttp(publicUri) || !validHttp(mailUri)
                    || publicUri.getRawQuery() != null || publicUri.getRawFragment() != null
                    || Base64.getDecoder().decode(signingKey).length != 32
                    || mailToken == null || mailToken.length() < 32) {
                throw new IllegalArgumentException("邀请入口、签名密钥或邮件调用配置不合法");
            }
        }
    }

    private static boolean validHttp(URI uri) {
        return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null;
    }
}
