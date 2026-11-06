package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 在线状态为可关闭的短期协作提示，不影响正文保存。 */
@ConfigurationProperties(prefix = "workstation.note-presence")
public record NotePresenceProperties(boolean enabled) {}
