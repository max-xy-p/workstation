package com.mike.workstation.service;

import com.mike.workstation.config.NotePresenceProperties;
import com.mike.workstation.domain.NotePresenceState;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NotePresenceParticipant;
import com.mike.workstation.dto.NotePresenceRequest;
import com.mike.workstation.dto.NotePresenceResponse;
import com.mike.workstation.exception.NotePresenceException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.NotePresenceMapper;
import com.mike.workstation.messaging.NotePresenceMessages;
import com.mike.workstation.security.CurrentPrincipalResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/** 每次查询与续租均重新授权；在线提示不提供排他编辑或正文写入能力。 */
@Service
public class NotePresenceService {
    private final NotePresenceProperties properties;
    private final CurrentPrincipalResolver principals;
    private final WorkspaceAuthorizationService authorization;
    private final NoteMapper notes;
    private final NotePresenceMapper members;
    private final NotePresenceStore store;
    private final ObjectProvider<NotePresenceMessages> messages;

    public NotePresenceService(NotePresenceProperties properties, CurrentPrincipalResolver principals,
            WorkspaceAuthorizationService authorization, NoteMapper notes, NotePresenceMapper members,
            NotePresenceStore store, ObjectProvider<NotePresenceMessages> messages) {
        this.properties = properties;
        this.principals = principals;
        this.authorization = authorization;
        this.notes = notes;
        this.members = members;
        this.store = store;
        this.messages = messages;
    }

    public String authorize(long workspaceId, long noteId, WorkspacePermission permission) {
        var principal = principals.require();
        if (principal.principalType() != PrincipalType.USER) {
            throw new WorkspaceAccessDeniedException("笔记在线状态仅供用户账号使用");
        }
        if (workspaceId <= 0 || noteId <= 0) {
            throw new IllegalArgumentException("笔记标识不合法");
        }
        authorization.require(workspaceId, permission);
        if (notes.findVersionById(workspaceId, noteId) == null) {
            throw new NotFoundException("笔记不存在");
        }
        return principal.subjectId();
    }

    public void requireEnabled() {
        if (!properties.enabled()) {
            throw new NotFoundException("笔记在线状态未启用");
        }
    }

    public NotePresenceResponse get(long workspaceId, long noteId) {
        authorize(workspaceId, noteId, WorkspacePermission.NOTE_READ);
        if (!properties.enabled()) {
            return new NotePresenceResponse(false, false, List.of());
        }
        try {
            var snapshot = store.snapshot(workspaceId, noteId);
            var participants = snapshot.isEmpty() ? List.<NotePresenceParticipant>of()
                    : members.currentMembers(workspaceId, snapshot.keySet().stream().sorted().toList()).stream()
                    .map(member -> new NotePresenceParticipant(member.getSubjectId(), member.getUsername(),
                            member.getDisplayName(), WorkspacePermission.forRole(member.getRole()).contains(WorkspacePermission.NOTE_WRITE)
                            ? snapshot.get(member.getSubjectId()) : NotePresenceState.VIEWING)).toList();
            return new NotePresenceResponse(true, true, participants);
        } catch (DataAccessException | NotePresenceException exception) {
            return new NotePresenceResponse(true, false, List.of());
        }
    }

    public void update(long workspaceId, long noteId, String session, NotePresenceRequest request) {
        if (request == null || request.state() == null || request.sequence() == null || request.sequence() < 1
                || !UUID.fromString(session).toString().equals(session)) {
            throw new IllegalArgumentException("在线状态参数不合法");
        }
        var permission = request.state() == NotePresenceState.EDITING ? WorkspacePermission.NOTE_WRITE : WorkspacePermission.NOTE_READ;
        String subject = authorize(workspaceId, noteId, permission);
        requireEnabled();
        try {
            if (store.update(workspaceId, noteId, subject, session, request.sequence(), request.state())) {
                messages.ifAvailable(sender -> sender.publish(workspaceId, noteId));
            }
        } catch (DataAccessException exception) {
            throw new NotePresenceException(false);
        }
    }
}
