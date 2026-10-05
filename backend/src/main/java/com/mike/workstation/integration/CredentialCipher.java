package com.mike.workstation.integration;

import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 凭证按版本加密，附加数据将其绑定到工作空间和平台实例。 */
@Component
public class CredentialCipher {
    private final IntegrationProperties properties;
    private final SecureRandom random = new SecureRandom();
    public CredentialCipher(IntegrationProperties properties) { this.properties = properties; }

    public String encrypt(String token, String context) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, properties.keyVersion(), nonce, context);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("数据源凭证加密失败");
        }
    }

    public String decrypt(String value, String version, String context) {
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] nonce = Base64.getDecoder().decode(parts[0]);
            if (nonce.length != 12) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, version, nonce, context)
                    .doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new BusinessException("数据源凭证无法解密，请检查密钥版本或重新配置凭证");
        }
    }

    private Cipher cipher(int mode, String version, byte[] nonce, String context) throws GeneralSecurityException {
        String encoded = properties.encryptionKeys() == null ? null : properties.encryptionKeys().get(version);
        if (encoded == null || encoded.isBlank()) throw new BusinessException("尚未配置数据源凭证加密密钥");
        byte[] key;
        try { key = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ex) { throw new BusinessException("数据源凭证加密密钥格式不合法"); }
        if (key.length != 32) throw new BusinessException("数据源凭证加密密钥必须为 32 字节");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
