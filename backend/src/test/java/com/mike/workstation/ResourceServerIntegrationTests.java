package com.mike.workstation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void protectedApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void protectedApiRejectsTokenWithoutOwnerRole() throws Exception {
        mockMvc.perform(get("/api/not-found").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void ownerTokenPassesSecurityFilter() throws Exception {
        mockMvc.perform(get("/api/not-found").with(jwt()
                        .jwt(token -> token.claim("preferred_username", "owner"))
                        .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPaginationReturnsUnifiedBadRequest() throws Exception {
        mockMvc.perform(get("/api/projects").param("size", "101").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/projects").param("page", "not-a-number").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void requestBodyRejectsFieldsOutsideOpenApiContract() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"architecture\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
