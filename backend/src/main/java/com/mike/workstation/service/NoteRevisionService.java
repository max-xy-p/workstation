package com.mike.workstation.service;

import com.mike.workstation.assembler.NoteRevisionResponseAssembler;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteRevision;
import com.mike.workstation.domain.NoteRevisionSource;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NoteRevisionResponse;
import com.mike.workstation.dto.NoteRevisionSummaryResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.NoteRevisionMapper;
import com.mike.workstation.security.CurrentPrincipalResolver;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** 历史快照的查询与写入；写入加入调用方的笔记事务。 */
@Service
public class NoteRevisionService {
    private final NoteRevisionMapper revisions;
    private final NoteMapper notes;
    private final WorkspaceAuthorizationService authorization;
    private final CurrentPrincipalResolver principals;

    public NoteRevisionService(
            NoteRevisionMapper revisions,
            NoteMapper notes,
            WorkspaceAuthorizationService authorization,
            CurrentPrincipalResolver principals) {
        this.revisions = revisions;
        this.notes = notes;
        this.authorization = authorization;
        this.principals = principals;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteRevisionSummaryResponse> list(
            Long workspaceId, Long noteId, Long upToVersion, int page, int size) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_READ);
        PageQuery query = PageQuery.of(page, size);
        if (upToVersion != null && upToVersion < 1) {
            throw new BusinessException("历史版本上界必须为正整数");
        }
        Long current = notes.findVersionById(workspaceId, noteId);
        RequestChecks.requiredExisting(current, "笔记不存在");
        long upper = upToVersion == null ? current : Math.min(current, upToVersion);
        return PageResponse.of(
                revisions.list(workspaceId, noteId, upper, query.offset(), query.size()).stream()
                        .map(NoteRevisionResponseAssembler::summary)
                        .toList(),
                page,
                size,
                revisions.count(workspaceId, noteId, upper));
    }

    public NoteRevisionResponse get(Long workspaceId, Long noteId, Long revisionId) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_READ);
        return NoteRevisionResponseAssembler.detail(require(workspaceId, noteId, revisionId));
    }

    public NoteRevision require(Long workspaceId, Long noteId, Long revisionId) {
        NoteRevision revision = revisions.find(workspaceId, noteId, revisionId);
        RequestChecks.requiredExisting(revision, "笔记历史不存在");
        return revision;
    }

    public void record(Note note, NoteRevisionSource source, Long restoredFromRevisionId) {
        if (source == NoteRevisionSource.UPDATE) {
            NoteRevision latest = revisions.latestForUpdate(note.getWorkspaceId(), note.getId());
            if (latest != null
                    && Objects.equals(latest.getTitle(), note.getTitle())
                    && Objects.equals(latest.getContent(), note.getContent())) {
                return;
            }
        }
        NoteRevision revision = new NoteRevision();
        revision.setWorkspaceId(note.getWorkspaceId());
        revision.setNoteId(note.getId());
        revision.setNoteVersion(note.getVersion());
        revision.setTitle(note.getTitle());
        revision.setContent(note.getContent());
        revision.setActorSubject(principals.require().subjectId());
        revision.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        revision.setSource(source);
        revision.setRestoredFromRevisionId(restoredFromRevisionId);
        revisions.insert(revision);
    }
}
