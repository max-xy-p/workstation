package com.mike.workstation.integration;

import com.mike.workstation.domain.DevelopmentItem;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 仅转换平台字段，不执行工作台业务授权与状态流转。 */
final class PlatformData {
    private PlatformData() {}

    static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    static LocalDateTime time(JsonNode node, String key) {
        String value = text(node, key);
        return value == null
                ? null
                : OffsetDateTime.parse(value)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
    }

    static DevelopmentItem item(
            JsonNode node,
            String kind,
            String idKey,
            String titleKey,
            String urlKey,
            String timeKey) {
        DevelopmentItem item = new DevelopmentItem();
        item.setKind(kind);
        item.setExternalId(text(node, idKey));
        item.setTitle(text(node, titleKey));
        item.setUrl(text(node, urlKey));
        item.setSourceUpdatedAt(time(node, timeKey));
        return item;
    }

    static String pipelineState(String state) {
        if (state == null) {
            return "UNKNOWN";
        }
        return switch (state) {
            case "success" -> "SUCCESS";
            case "neutral" -> "NEUTRAL";
            case "skipped" -> "SKIPPED";
            case "failure", "failed", "timed_out", "action_required", "startup_failure" -> "FAILED";
            case "cancelled", "canceled" -> "CANCELLED";
            case "in_progress", "running" -> "RUNNING";
            case "queued",
                            "pending",
                            "created",
                            "waiting",
                            "waiting_for_resource",
                            "preparing",
                            "scheduled",
                            "manual",
                            "requested" ->
                    "PENDING";
            default -> "UNKNOWN";
        };
    }
}
