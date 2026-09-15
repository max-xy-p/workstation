package com.mike.workstation.assembler;

import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.dto.NoteFolderResponse;

public final class NoteFolderResponseAssembler {
    private NoteFolderResponseAssembler() {
    }

    public static NoteFolderResponse toResponse(NoteFolder folder) {
        return new NoteFolderResponse(
                folder.getId(), folder.getName(), folder.getParentId(), folder.getCreatedAt(), folder.getUpdatedAt()
        );
    }
}
