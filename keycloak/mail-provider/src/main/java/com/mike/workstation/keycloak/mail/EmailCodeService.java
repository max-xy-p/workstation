package com.mike.workstation.keycloak.mail;

import org.keycloak.models.SingleUseObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** 验证码保存在 Keycloak 共享的一次性对象存储中，原文仅在发信调用期间存在。 */
final class EmailCodeService {
    static final long LIFETIME_SECONDS = 300;
    private static final int MAX_ATTEMPTS = 5;
    private static final String BINDING = "binding";
    private static final String EXPIRES = "expires";
    private static final String ACTIVE_SUFFIX = ":active";
    private static final String PREFIX = "workstation-email-code:";
    private final SingleUseObjectProvider store;
    private final Clock clock;
    private final SecureRandom random;
    private final SecretKey secret;

    EmailCodeService(SingleUseObjectProvider store, SecretKey secret) {
        this(store, Clock.systemUTC(), new SecureRandom(), secret);
    }

    EmailCodeService(
            SingleUseObjectProvider store, Clock clock, SecureRandom random, SecretKey secret) {
        this.store = store;
        this.clock = clock;
        this.random = random;
        this.secret = secret;
    }

    record Binding(String realm, String user, String email, String flow, String purpose) {
        String ownerKey() {
            return PREFIX + digest(realm + "|" + user + "|" + purpose);
        }

        String fingerprint() {
            return digest(ownerKey() + "|" + email + "|" + flow);
        }
    }

    record Issued(String id, String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "Issued[验证码已隐藏]";
        }
    }

    /** 页面刷新不会发信；账号级冷却与额度同时约束多个浏览器、标签页和节点。 */
    Issued issue(Binding binding, String currentId, boolean resend, String remoteAddress) {
        if (currentId != null && !resend) {
            return null;
        }
        long now = clock.instant().getEpochSecond();
        String owner = binding.ownerKey();
        if (!store.putIfAbsent(owner + ":cooldown", 60)) {
            throw new IllegalStateException("发送过于频繁，请至少等待 60 秒后再试。");
        }
        if (!reserve(owner + ":hour:" + now / 3600, 5, 3600)
                || !reserve(
                        PREFIX
                                + "ip:"
                                + digest(binding.realm() + "|" + remoteAddress)
                                + ":"
                                + now / 600,
                        20,
                        600)) {
            throw new IllegalStateException("验证码发送次数已达上限，请稍后再试。");
        }
        String id = UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String salt = UUID.randomUUID().toString();
        Instant expires = clock.instant().plusSeconds(LIFETIME_SECONDS);
        Map<String, String> previous = store.remove(owner + ACTIVE_SUFFIX);
        if (previous != null) {
            invalidate(previous.get("id"));
        }
        store.put(
                PREFIX + id,
                LIFETIME_SECONDS,
                Map.of(
                        "binding",
                        binding.fingerprint(),
                        "salt",
                        salt,
                        "digest",
                        codeDigest(salt, code, binding.fingerprint()),
                        "expires",
                        Long.toString(expires.getEpochSecond())));
        store.put(owner + ACTIVE_SUFFIX, LIFETIME_SECONDS, Map.of("id", id));
        return new Issued(id, code, expires);
    }

    boolean verify(Binding binding, String id, String code) {
        if (id == null) {
            return false;
        }
        String key = PREFIX + id;
        Map<String, String> state = store.get(key);
        if (!matches(binding, id, state)) {
            return false;
        }
        long remaining = Long.parseLong(state.get(EXPIRES)) - clock.instant().getEpochSecond();
        // 每次尝试通过原子占位消耗额度，避免并发请求丢失计数。
        if (remaining <= 0 || !reserve(key + ":attempt", MAX_ATTEMPTS, remaining)) {
            return false;
        }
        if (code == null || code.length() != 6 || !code.matches("[0-9]{6}")) {
            return false;
        }
        byte[] supplied =
                codeDigest(state.get("salt"), code, binding.fingerprint())
                        .getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(
                supplied, state.get("digest").getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        // remove 保证集群内只有一个成功者；重发先移除旧对象，旧码不能再次生效。
        return matches(binding, id, store.remove(key));
    }

    private boolean matches(Binding binding, String id, Map<String, String> state) {
        if (state == null
                || !binding.fingerprint().equals(state.get(BINDING))
                || Long.parseLong(state.get(EXPIRES)) <= clock.instant().getEpochSecond()) {
            return false;
        }
        Map<String, String> active = store.get(binding.ownerKey() + ACTIVE_SUFFIX);
        return active != null && id.equals(active.get("id"));
    }

    void invalidate(String id) {
        if (id != null) {
            store.remove(PREFIX + id);
        }
    }

    private boolean reserve(String key, int limit, long seconds) {
        for (int slot = 0; slot < limit; slot++) {
            if (store.putIfAbsent(key + ":" + slot, seconds)) {
                return true;
            }
        }
        return false;
    }

    private String codeDigest(String salt, String code, String binding) {
        try {
            // 摘要使用 Realm 的 HMAC 密钥；仅泄露验证码缓存不足以离线枚举六位码。
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secret);
            return HexFormat.of()
                    .formatHex(
                            mac.doFinal(
                                    ("workstation-email-code|" + salt + "|" + code + "|" + binding)
                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("无法计算验证码摘要", error);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法计算验证码摘要", error);
        }
    }
}
