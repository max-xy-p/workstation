package com.mike.workstation.service;

import com.mike.workstation.domain.NotificationResourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 对内部命令也校验长度、时效和目标，避免错误适配器产生不可控通知。 */
@Component
public class NotificationCodec {
    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
    private final ObjectMapper json;

    public NotificationCodec(ObjectMapper json) {
        this.json = json;
    }

    public NotificationCommand normalize(NotificationCommand command, Instant now) {
        if (command == null || command.workspaceId() == null || command.workspaceId() <= 0
                || command.type() == null || !TYPE.matcher(command.type()).matches()
                || command.title() == null || command.title().isBlank() || command.title().length() > 200
                || command.body() == null || command.body().length() > 2000
                || command.resourceType() == null || command.occurredAt() == null
                || !command.occurredAt().plus(7, ChronoUnit.DAYS).isAfter(now)
                || command.occurredAt().isAfter(now.plusSeconds(300))) {
            throw new IllegalArgumentException("通知命令、正文、目标或发生时间不合法");
        }
        canonicalId(command.eventId());
        validateTarget(command);
        if (command.recipients() == null || command.recipients().isEmpty() || command.recipients().size() > 1000) {
            throw new IllegalArgumentException("通知接收者数量必须在 1 到 1000 之间");
        }
        List<String> recipients = command.recipients().stream().map(NotificationCodec::recipient).distinct().sorted().toList();
        return new NotificationCommand(command.eventId(), command.workspaceId(), command.type(),
                command.title().trim(), command.body(), command.resourceType(), command.resourceId(),
                command.occurredAt(), recipients);
    }

    private static void validateTarget(NotificationCommand command) {
        if (command.resourceType() == NotificationResourceType.WORKSPACE) {
            if (command.resourceId() != null) {
                throw new IllegalArgumentException("空间通知不应携带其他资源标识");
            }
        } else if (command.resourceId() == null || command.resourceId() <= 0) {
            throw new IllegalArgumentException("资源通知必须携带有效资源标识");
        }
    }

    private static String recipient(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("通知接收者标识不合法");
        }
        return value;
    }

    public static String canonicalId(String id) {
        if (id == null || !UUID.fromString(id).toString().equals(id)) {
            throw new IllegalArgumentException("通知事件标识必须为规范 UUID");
        }
        return id;
    }

    public String encode(NotificationCommand command) {
        String payload = json.writeValueAsString(command);
        if (payload.length() > 400000) {
            throw new IllegalArgumentException("通知事件内容过大");
        }
        return payload;
    }

    public String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算通知事件摘要");
        }
    }

}
