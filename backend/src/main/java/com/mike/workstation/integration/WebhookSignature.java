package com.mike.workstation.integration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 对原始字节验签，不重序列化 JSON；比较使用常量时间摘要比较。 */
public final class WebhookSignature {
    private WebhookSignature() { }
    public static boolean valid(String secret,byte[] body,String signature) {
        if(signature==null || !signature.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature.substring(7)));
        } catch(GeneralSecurityException ex) { throw new IllegalStateException("运行环境不支持 Webhook 签名校验",ex); }
    }
    public static String hash(byte[] body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch(GeneralSecurityException ex) { throw new IllegalStateException("运行环境不支持 SHA-256",ex); }
    }
}
