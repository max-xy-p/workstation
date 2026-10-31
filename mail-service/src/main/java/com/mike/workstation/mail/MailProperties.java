package com.mike.workstation.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("mail")
public record MailProperties(
        String keycloakToken,
        String backendToken,
        String encryptionKey,
        String resendApiKey,
        @NotBlank @Email @Pattern(regexp = "[^\\r\\n<>]+") String from,
        @NotBlank @Pattern(regexp = "[^\\r\\n<>]+") String fromName,
        String replyTo,
        boolean workerEnabled,
        String webhookSecret) {
    public MailProperties {
        if (keycloakToken == null
                || keycloakToken.length() < 32
                || backendToken == null
                || backendToken.length() < 32
                || encryptionKey == null
                || encryptionKey.isBlank()
                || resendApiKey == null
                || resendApiKey.isBlank()) {
            throw new IllegalArgumentException("必须配置完整的邮件凭据和至少 32 字符的内部调用凭据");
        }
        if (keycloakToken.equals(backendToken)) {
            throw new IllegalArgumentException("邮件调用方必须使用不同的凭据");
        }
        if (replyTo != null
                && !replyTo.isEmpty()
                && (replyTo.length() > 254
                        || !replyTo.matches("[^\\s<>@]++@[^\\s<>@.]++(?:\\.[^\\s<>@.]++)++"))) {
            throw new IllegalArgumentException("邮件回复地址格式无效");
        }
    }

    @Override
    public String toString() {
        return "MailProperties[凭据已隐藏]";
    }
}
