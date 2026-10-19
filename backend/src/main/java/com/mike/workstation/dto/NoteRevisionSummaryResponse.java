package com.mike.workstation.dto;

import com.mike.workstation.domain.NoteRevisionSource;
import java.time.OffsetDateTime;

/** 历史列表只传递摘要，不批量传输正文。 */
public record NoteRevisionSummaryResponse(
        Long id, Long noteId, Long noteVersion, String title, String actorSubject,
        String actorDisplayName, OffsetDateTime createdAt, NoteRevisionSource source,
        Long restoredFromRevisionId
) { }
