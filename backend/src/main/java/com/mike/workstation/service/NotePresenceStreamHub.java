package com.mike.workstation.service;

import com.mike.workstation.config.NotePresenceProperties;
import com.mike.workstation.exception.NotePresenceException;
import org.springframework.stereotype.Component;

/** 在线状态与通知共用 SSE 传输实现，各自保持主题与容量隔离。 */
@Component
public class NotePresenceStreamHub extends RefreshStreamHub {
    public NotePresenceStreamHub(NotePresenceProperties properties) {
        super(properties.enabled(), 500, 5, () -> new NotePresenceException(true));
    }

    public static String topic(long workspaceId, long noteId) {
        return workspaceId + ":" + noteId;
    }
}
