package com.mike.workstation.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NotePresenceResponse;
import com.mike.workstation.exception.GlobalExceptionHandler;
import com.mike.workstation.exception.NotePresenceException;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.NotePresenceService;
import com.mike.workstation.service.NotePresenceStreamHub;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class NotePresenceControllerTests {
    @Test
    void snapshotIsNotCachedAndWriteFailuresHaveDedicatedStatusCodes() throws Exception {
        var service = mock(NotePresenceService.class);
        var hub = mock(NotePresenceStreamHub.class);
        var principals = mock(CurrentPrincipalResolver.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NotePresenceController(service, hub, principals))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String path = "/api/workspaces/1/notes/2/presence";
        when(service.get(1, 2)).thenReturn(new NotePresenceResponse(true, false, List.of()));
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(false));
        String session = UUID.randomUUID().toString();
        String target = path + "/sessions/" + session;
        mvc.perform(put(target).contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"VIEWING\",\"sequence\":1}"))
                .andExpect(status().isNoContent());
        doThrow(new NotePresenceException(false)).when(service).update(eq(1L), eq(2L), eq(session), any());
        mvc.perform(put(target).contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"VIEWING\",\"sequence\":2}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PRESENCE_UNAVAILABLE"));
        doThrow(new NotePresenceException(true)).when(service).update(eq(1L), eq(2L), eq(session), any());
        mvc.perform(put(target).contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"VIEWING\",\"sequence\":3}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("PRESENCE_LIMIT"));
    }

    @Test
    void streamContainsOnlyRefreshHintsAndLimitsAreJsonEvenWithSseAccept() throws Exception {
        var service = mock(NotePresenceService.class);
        var hub = mock(NotePresenceStreamHub.class);
        var principals = mock(CurrentPrincipalResolver.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NotePresenceController(service, hub, principals))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        Instant expires = Instant.now().plusSeconds(60);
        when(service.authorize(1, 2, WorkspacePermission.NOTE_READ)).thenReturn("user");
        when(principals.requireExpiresAt()).thenReturn(expires);
        var emitter = new SseEmitter(1000L);
        when(hub.open("1:2", "user", expires)).thenReturn(emitter);
        String path = "/api/workspaces/1/notes/2/presence/stream";
        var result = mvc.perform(get(path).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
        emitter.send(SseEmitter.event().name("refresh").data("{}"));
        assertThat(result.getResponse().getContentAsString()).contains("event:refresh", "data:{}");
        emitter.complete();
        when(hub.open("1:2", "user", expires)).thenThrow(new NotePresenceException(true));
        mvc.perform(get(path).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PRESENCE_LIMIT"));
        verify(service, times(2)).requireEnabled();
    }
}
