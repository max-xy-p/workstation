package com.mike.workstation.controller;

import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NotePresenceRequest;
import com.mike.workstation.dto.NotePresenceResponse;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.NotePresenceService;
import com.mike.workstation.service.NotePresenceStreamHub;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** HTTP 管理短租约及受权快照，SSE 只提醒重新读取。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/notes/{id}/presence")
public class NotePresenceController {
    private final NotePresenceService service;
    private final NotePresenceStreamHub streams;
    private final CurrentPrincipalResolver principals;

    public NotePresenceController(NotePresenceService service, NotePresenceStreamHub streams,
            CurrentPrincipalResolver principals) {
        this.service = service;
        this.streams = streams;
        this.principals = principals;
    }

    @GetMapping
    public ResponseEntity<NotePresenceResponse> get(@PathVariable long workspaceId, @PathVariable long id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.get(workspaceId, id));
    }

    @PutMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable long workspaceId, @PathVariable long id, @PathVariable String sessionId,
            @RequestBody NotePresenceRequest request) {
        service.update(workspaceId, id, sessionId, request);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable long workspaceId, @PathVariable long id) {
        String subject = service.authorize(workspaceId, id, WorkspacePermission.NOTE_READ);
        service.requireEnabled();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Accel-Buffering", "no")
                .body(streams.open(NotePresenceStreamHub.topic(workspaceId, id), subject, principals.requireExpiresAt()));
    }
}
