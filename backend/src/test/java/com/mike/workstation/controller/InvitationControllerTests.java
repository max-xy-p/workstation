package com.mike.workstation.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.dto.InvitationRequest;
import com.mike.workstation.dto.InvitationTokenRequest;
import com.mike.workstation.exception.GlobalExceptionHandler;
import com.mike.workstation.exception.InvitationConflictException;
import com.mike.workstation.service.InvitationMailClient;
import com.mike.workstation.service.InvitationService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

class InvitationControllerTests {
    @Test
    void routesPreserveWorkspaceRequestIdTokenAndConflictCode() throws Exception {
        var service = mock(InvitationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new InvitationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String id = "12345678-1234-1234-1234-123456789abc";
        mvc.perform(get("/api/workspaces/9/invitations")).andExpect(status().isOk());
        verify(service).list(9L);
        mvc.perform(post("/api/workspaces/9/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + id + "\",\"email\":\"g@example.com\",\"role\":\"EDITOR\"}")).andExpect(status().isCreated());
        var request = new InvitationRequest(id, "g@example.com", com.mike.workstation.domain.WorkspaceRole.EDITOR);
        verify(service).create(9L, request);
        when(service.create(9L, request)).thenThrow(new InvitationConflictException("标识冲突"));
        mvc.perform(post("/api/workspaces/9/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + id + "\",\"email\":\"g@example.com\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_CONFLICT"));
        mvc.perform(post("/api/workspaces/9/invitations/" + id + "/revoke")).andExpect(status().isOk());
        verify(service).revoke(9L, id);
        mvc.perform(post("/api/invitations/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"sample\"}")).andExpect(status().isOk());
        verify(service).preview(new InvitationTokenRequest("sample"));
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"sample\"}")).andExpect(status().isOk());
        verify(service).accept(new InvitationTokenRequest("sample"));
    }

    @Test
    void internalMailClientUsesDedicatedTokenAndOnlyAccepts202() throws Exception {
        var received = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/mail/messages", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders("POST".equals(exchange.getRequestMethod()) ? 202 : 500, -1);
            exchange.close();
        });
        server.start();
        try {
            var properties = new InvitationProperties(false, "", "",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "internal-only-token");
            var client = new InvitationMailClient(properties, new ObjectMapper());
            var request = new InvitationMailClient.MailRequest("workspace-invitation/test", "g@example.com",
                    "邀请", "内容", OffsetDateTime.now().plusHours(1));
            client.enqueue(request);
            assertThat(authorization.get()).isEqualTo("Bearer internal-only-token");
            assertThat(received.get()).contains("\"requestKey\":\"workspace-invitation/test\"", "\"to\":\"g@example.com\"");
        } finally {
            server.stop(0);
        }
    }
}
