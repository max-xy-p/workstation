package com.mike.workstation.mail;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class MailService {
    private final MailTaskRepository repository;
    private final PayloadCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;

    public MailService(
            MailTaskRepository repository, PayloadCipher cipher, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    public MailStatus enqueue(String caller, MailRequest request) {
        String serialized = json.writeValueAsString(request);
        String fingerprint = fingerprint(serialized);
        MailTask existing = repository.byKey(caller, request.requestKey());
        if (existing != null) {
            return identical(existing, fingerprint);
        }
        Instant now = clock.instant();
        // 小于 Resend 的 24 小时幂等窗口，崩溃恢复也不能在窗口外自动重复投递。
        if (!request.expiresAt().isAfter(now)
                || request.expiresAt().isAfter(now.plusSeconds(23L * 3600))
                || ((request.text() == null || request.text().isBlank())
                        && (request.html() == null || request.html().isBlank()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮件必须包含正文，且到期时间须在未来 23 小时内");
        }
        String id = UUID.randomUUID().toString();
        try {
            repository.insert(
                    id, caller, request, fingerprint, cipher.encrypt(id, serialized), now);
        } catch (DuplicateKeyException error) {
            return identical(repository.byKey(caller, request.requestKey()), fingerprint);
        }
        return status(caller, id);
    }

    public MailStatus status(String caller, String id) {
        MailTask task = repository.byId(caller, id);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "邮件任务不存在");
        }
        return task.view();
    }

    private MailStatus identical(MailTask task, String fingerprint) {
        if (task == null || !fingerprint.equals(task.fingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同请求标识不能用于不同邮件");
        }
        return task.view();
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法计算邮件请求摘要");
        }
    }
}
