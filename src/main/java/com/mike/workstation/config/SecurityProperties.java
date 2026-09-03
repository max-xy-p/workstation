package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 单用户站点的管理员登录配置。
 *
 * @param adminUsername 管理员用户名
 * @param adminPasswordHash BCrypt 密码散列，禁止配置明文密码
 */
@ConfigurationProperties(prefix = "workstation.security")
public record SecurityProperties(String adminUsername, String adminPasswordHash) {
    public SecurityProperties {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("APP_ADMIN_USERNAME must not be blank");
        }
        if (adminPasswordHash == null || !adminPasswordHash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalArgumentException("APP_ADMIN_PASSWORD_HASH must be a BCrypt hash");
        }
    }
}
