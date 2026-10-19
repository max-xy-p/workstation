package com.mike.workstation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mike.workstation.service.WorkspaceAuthorizationService;
import com.mike.workstation.service.NoteService;
import com.mike.workstation.exception.NoteVersionConflictException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "DB_URL=jdbc:mysql://localhost:3306/workstation",
        "DB_USERNAME=test",
        "DB_PASSWORD=test",
        "DASHBOARD_RECENT_LIMIT=5",
        "NOTE_IMPORT_ALLOWED_ROOTS=D:/notes",
        "NOTE_IMPORT_MAX_FILE_BYTES=1048576"
})
@AutoConfigureMockMvc
class ResourceServerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private WorkspaceAuthorizationService authorizationService;
    @MockitoBean
    private NoteService noteService;
    @MockitoBean
    private com.mike.workstation.service.NoteRevisionService noteRevisions;

    @MockitoBean
    private com.mike.workstation.service.WorkspaceService workspaceService;

    @Test
    void noteHistoryEndpointsKeepWorkspaceAndConflictContract() throws Exception {
        when(noteRevisions.list(1L,2L,5L,0,20)).thenReturn(com.mike.workstation.dto.PageResponse.of(java.util.List.of(),0,20,0));
        mockMvc.perform(get("/api/workspaces/1/notes/2/revisions").param("upToVersion","5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/workspaces/1/notes/2/revisions")).andExpect(status().isUnauthorized());
        when(noteService.restoreRevision(eq(1L),eq(2L),eq(3L),any())).thenThrow(new NoteVersionConflictException());
        mockMvc.perform(post("/api/workspaces/1/notes/2/revisions/3/restore")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTE_VERSION_CONFLICT"));
        verify(noteRevisions).list(1L,2L,5L,0,20);
    }

    @Test
    void noteHistoryRestoreRejectsForgedAuthorFields() throws Exception {
        mockMvc.perform(post("/api/workspaces/1/notes/2/revisions/3/restore")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":5,\"actorSubject\":\"forged\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberSearchKeepsExistingParameterAndReturnsDisplayNames() throws Exception {
        when(workspaceService.findMemberCandidates(1L, "张"))
                .thenReturn(java.util.List.of(new com.mike.workstation.dto.WorkspaceMemberCandidate(
                        "subject-2", "zhang", "张三")));
        mockMvc.perform(get("/api/workspaces/1/member-candidates").param("username", "张")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("张三"));
        mockMvc.perform(get("/api/workspaces/1/member-candidates")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspaces/1/member-candidates").param("username", "张"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/workspaces/1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void protectedApiRejectsTokenWithoutSystemRole() throws Exception {
        mockMvc.perform(get("/api/not-found").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void workstationUserTokenPassesSecurityFilter() throws Exception {
        mockMvc.perform(get("/api/not-found").with(jwt()
                        .jwt(token -> token.claim("preferred_username", "owner"))
                        .authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPaginationReturnsUnifiedBadRequest() throws Exception {
        mockMvc.perform(get("/api/workspaces/1/projects").param("size", "101").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/workspaces/1/projects").param("page", "not-a-number").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void requestBodyRejectsFieldsOutsideOpenApiContract() throws Exception {
        mockMvc.perform(post("/api/workspaces/1/tags")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"architecture\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void staleNoteUpdateReturnsStableConflictCode() throws Exception {
        when(noteService.update(eq(1L), eq(2L), any())).thenThrow(new NoteVersionConflictException());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/workspaces/1/notes/2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"标题\",\"content\":\"正文\",\"tagIds\":[],\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTE_VERSION_CONFLICT"));
    }

    @Test
    void trashOperationsUseDedicatedAuthenticatedEndpoints() throws Exception {
        var authenticated = jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKSTATION_USER"));

        mockMvc.perform(get("/api/workspaces/1/notes/trash").with(authenticated))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/1/notes/2/restore").with(authenticated))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/workspaces/1/notes/2/permanent").with(authenticated))
                .andExpect(status().isNoContent());

        verify(noteService).listDeleted(1L, null, 0, 20);
        verify(noteService).restore(1L, 2L);
        verify(noteService).permanentlyDelete(1L, 2L);
    }
}
