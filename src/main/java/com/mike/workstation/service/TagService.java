package com.mike.workstation.service;

import com.mike.workstation.domain.Tag;
import com.mike.workstation.dto.TagRequest;
import com.mike.workstation.mapper.TagMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public List<Tag> list() {
        return tagMapper.list();
    }

    public Tag get(Long id) {
        Tag tag = tagMapper.findById(id);
        RequestChecks.requiredExisting(tag, "标签不存在");
        return tag;
    }

    @Transactional
    public Tag create(TagRequest request) {
        validate(request);
        Tag tag = new Tag();
        tag.setName(request.name());
        LocalDateTime now = LocalDateTime.now();
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        tagMapper.insert(tag);
        return get(tag.getId());
    }

    @Transactional
    public Tag update(Long id, TagRequest request) {
        validate(request);
        get(id);
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(request.name());
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.update(tag);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        tagMapper.deleteTagLinks(id);
        tagMapper.delete(id);
    }

    private void validate(TagRequest request) {
        RequestChecks.requiredObject(request, "标签请求");
        RequestChecks.requiredText(request.name(), "标签名称");
    }
}
