package com.mike.workstation.assembler;

import com.mike.workstation.domain.Tag;
import com.mike.workstation.dto.TagResponse;
import java.util.List;

public final class TagResponseAssembler {
    private TagResponseAssembler() {
    }

    public static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt(), tag.getUpdatedAt());
    }

    public static List<TagResponse> toResponses(List<Tag> tags) {
        return tags.stream().map(TagResponseAssembler::toResponse).toList();
    }
}
