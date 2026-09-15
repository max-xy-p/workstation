package com.mike.workstation.assembler;

import com.mike.workstation.domain.Note;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteSummaryResponse;

public final class NoteResponseAssembler {
    private NoteResponseAssembler() {
    }

    public static NoteSummaryResponse toSummary(Note note) {
        return new NoteSummaryResponse(
                note.getId(), note.getTitle(), note.getFolderId(), note.getFolderName(),
                note.getProjectId(), note.getProjectName(), note.getVersion(),
                TagResponseAssembler.toResponses(note.getTags()),
                ApiTime.toOffset(note.getCreatedAt()), ApiTime.toOffset(note.getUpdatedAt())
        );
    }

    public static NoteDetailResponse toDetail(Note note) {
        return new NoteDetailResponse(
                note.getId(), note.getTitle(), note.getContent(), note.getFolderId(), note.getFolderName(),
                note.getProjectId(), note.getProjectName(), note.getVersion(),
                TagResponseAssembler.toResponses(note.getTags()),
                ApiTime.toOffset(note.getCreatedAt()), ApiTime.toOffset(note.getUpdatedAt())
        );
    }
}
