package com.mike.workstation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "DB_URL=jdbc:mysql://localhost:3306/workstation",
        "DB_USERNAME=test",
        "DB_PASSWORD=test",
        "APP_ADMIN_USERNAME=owner",
        "APP_ADMIN_PASSWORD_HASH=$2a$10$LKcG8Fmj65ZSAK2augYy/.n66.VjHTNeHpKUH8DeWSbBJhljfiU7O",
        "DASHBOARD_RECENT_LIMIT=5",
        "NOTE_IMPORT_ALLOWED_ROOTS=D:/notes",
        "NOTE_IMPORT_MAX_FILE_BYTES=1048576"
})
@AutoConfigureMockMvc
class AuthenticationIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void csrfEndpointIsAvailableBeforeLogin() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginRequiresCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "owner")
                        .param("password", "password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanLoginAndReadCurrentSession() throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "owner")
                        .param("password", "password"))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.owner").value(true));
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "owner")
                        .param("password", "incorrect"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }
}
