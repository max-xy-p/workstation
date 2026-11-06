package com.mike.workstation.dto;

import com.mike.workstation.domain.NotePresenceState;

/** 同一页面的请求序号递增，避免迟到的续租覆盖离开操作。 */
public record NotePresenceRequest(NotePresenceState state, Integer sequence) {}
