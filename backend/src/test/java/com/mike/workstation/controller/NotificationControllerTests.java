package com.mike.workstation.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mike.workstation.dto.NotificationReadAllRequest;
import com.mike.workstation.dto.NotificationState;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.exception.GlobalExceptionHandler;
import com.mike.workstation.exception.NotificationStreamLimitException;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.NotificationService;
import com.mike.workstation.service.NotificationStreamHub;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class NotificationControllerTests {
    @Test
    void httpRoutesPreserveCurrentUserScopePaginationAndReadBoundary() throws Exception {
        var service = mock(NotificationService.class);
        var hub = mock(NotificationStreamHub.class);
        var principals = mock(CurrentPrincipalResolver.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service, hub, principals))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.state()).thenReturn(new NotificationState(true, 2, 4, 6));
        when(service.list(0, 20, false, null)).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        mvc.perform(get("/api/notifications/state")).andExpect(status().isOk()).andExpect(jsonPath("$.latestSequence").value(4));
        mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/notifications").param("page", "1").param("size", "5").param("unreadOnly", "true").param("upToSequence", "3"))
                .andExpect(status().isOk());
        verify(service).list(1, 5, true, 3L);
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/notifications/" + id)).andExpect(status().isOk());
        verify(service).get(id);
        mvc.perform(put("/api/notifications/" + id + "/read")).andExpect(status().isNoContent());
        verify(service).read(id);
        mvc.perform(put("/api/notifications/read-all").contentType(MediaType.APPLICATION_JSON).content("{\"throughSequence\":3}"))
                .andExpect(status().isNoContent());
        verify(service).readAll(new NotificationReadAllRequest(3L));
        mvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void streamUsesNoBufferHeadersAndReturnsSpecificConnectionLimit() throws Exception {
        var service = mock(NotificationService.class);
        var hub = mock(NotificationStreamHub.class);
        var principals = mock(CurrentPrincipalResolver.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service, hub, principals))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        Instant expires = Instant.now().plusSeconds(60);
        when(service.requireUser()).thenReturn("user");
        when(principals.requireExpiresAt()).thenReturn(expires);
        var emitter = new SseEmitter(1000L);
        when(hub.open("user", expires)).thenReturn(emitter);
        var response = mvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        emitter.send(SseEmitter.event().name("refresh").data("{}"));
        assertThat(response.getResponse().getContentAsString()).contains("event:refresh", "data:{}");
        emitter.complete();
        when(hub.open("user", expires)).thenThrow(new NotificationStreamLimitException());
        mvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_STREAM_LIMIT"));
    }
}
