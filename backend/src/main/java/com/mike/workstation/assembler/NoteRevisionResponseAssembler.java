package com.mike.workstation.assembler;

import com.mike.workstation.domain.NoteRevision;
import com.mike.workstation.dto.NoteRevisionResponse;
import com.mike.workstation.dto.NoteRevisionSummaryResponse;

public final class NoteRevisionResponseAssembler {
    private NoteRevisionResponseAssembler() { }

    public static NoteRevisionSummaryResponse summary(NoteRevision revision) {
        return new NoteRevisionSummaryResponse(revision.getId(), revision.getNoteId(), revision.getNoteVersion(),
                revision.getTitle(), revision.getActorSubject(), revision.getActorDisplayName(),
                ApiTime.toOffset(revision.getCreatedAt()), revision.getSource(), revision.getRestoredFromRevisionId());
    }

    public static NoteRevisionResponse detail(NoteRevision revision) {
        return new NoteRevisionResponse(revision.getId(), revision.getNoteId(), revision.getNoteVersion(),
                revision.getTitle(), revision.getActorSubject(), revision.getActorDisplayName(),
                ApiTime.toOffset(revision.getCreatedAt()), revision.getSource(), revision.getRestoredFromRevisionId(),
                revision.getContent());
    }
}
