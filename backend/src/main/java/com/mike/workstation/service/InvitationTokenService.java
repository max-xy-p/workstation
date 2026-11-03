package com.mike.workstation.service;

import com.mike.workstation.config.InvitationProperties;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 随机 UUID 与独立 HMAC 密钥生成稳定凭据，使投递重试可重建完全相同的邮件。 */
@Component
public class InvitationTokenService {
    private final InvitationProperties properties;

    public InvitationTokenService(InvitationProperties properties) {
        this.properties = properties;
    }

    public String token(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(properties.signingKey()), "HmacSHA256"));
            return id + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(("workspace-invitation/" + id).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成邀请凭据");
        }
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算邀请摘要");
        }
    }
}
