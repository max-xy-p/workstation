package com.mike.workstation.dto;

import java.util.List;

/** 不可用与无人在线必须明确区分，避免误导协作者。 */
public record NotePresenceResponse(boolean enabled, boolean available,
        List<NotePresenceParticipant> participants) {}
