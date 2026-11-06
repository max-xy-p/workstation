package com.mike.workstation.dto;

import com.mike.workstation.domain.NotePresenceState;

/** 昵称由当前成员资料决定，不接受浏览器上报身份。 */
public record NotePresenceParticipant(String subjectId, String username, String displayName,
        NotePresenceState state) {}
