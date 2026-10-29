package com.mike.workstation.mail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PayloadCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PayloadCipher(MailProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.encryptionKey());
        if (bytes.length != 32) {
            throw new IllegalArgumentException("邮件加密密钥必须为 Base64 编码的 32 字节随机密钥");
        }
        key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String id, String plaintext) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, id, nonce, plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String id, String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("邮件内容解密失败");
        }
        return new String(crypt(Cipher.DECRYPT_MODE, id, Base64.getDecoder().decode(parts[0]),
                Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    }

    private byte[] crypt(int mode, String id, byte[] nonce, byte[] value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("邮件内容加解密失败");
        }
    }
}
