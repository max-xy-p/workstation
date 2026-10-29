package com.mike.workstation.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record MailRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_.:/-]+") String requestKey,
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[^\\s<>@]+@[^\\s<>@]+") String to,
        @NotBlank @Size(max = 200) @Pattern(regexp = "[^\\r\\n]+") String subject,
        @Size(max = 100000) String text,
        @Size(max = 100000) String html,
        @NotNull Instant expiresAt) {
    // 验证链接和正文不得因默认 record.toString() 意外进入日志。
    @Override
    public String toString() {
        return "MailRequest[内容已隐藏]";
    }
}
