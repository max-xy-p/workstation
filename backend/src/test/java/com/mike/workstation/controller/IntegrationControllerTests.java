package com.mike.workstation.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.domain.*;
import com.mike.workstation.service.IntegrationService;
import com.mike.workstation.exception.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationControllerTests {
    private final IntegrationService service=mock(IntegrationService.class);
    private MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.standaloneSetup(new IntegrationController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @Test void readEndpointsUseWorkspaceScopedPaginationAndSeparateExternalKinds() throws Exception {
        mvc.perform(get("/api/workspaces/9/integrations").param("page","2").param("size","30")).andExpect(status().isOk());
        verify(service).list(9L,2,30);
        for(String path:List.of("/integrations/2/repositories","/integrations/2/sync-runs","/integrations/2/sync-runs/3",
                "/integrations/2/sync-runs/3/items","/projects/20/repositories","/projects/20/development","/development/repositories"))
            mvc.perform(get("/api/workspaces/9"+path)).andExpect(status().isOk());
        mvc.perform(get("/api/workspaces/9/projects/20/development/items").param("kind","PIPELINE")).andExpect(status().isOk());
        verify(service).developmentItems(9L,20L,DevelopmentKind.PIPELINE,0,20);
        mvc.perform(get("/api/workspaces/9/projects/20/development/items").param("kind","INVALID")).andExpect(status().isBadRequest());
    }
    @Test void createUpdateValidateEnableAndImportHaveExplicitHttpSemantics() throws Exception {
        String body="""
                {"name":"连接","provider":"GITHUB","baseUrl":"https://github.com","token":"test",
                 "scope":"OWNED","includeForks":false,"includeArchived":false,"autoImport":false,
                 "scheduleMinutes":0,"expectedVersion":null,"sharingConfirmed":false}
                """;
        mvc.perform(post("/api/workspaces/9/integrations").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(patch("/api/workspaces/9/integrations/2").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/workspaces/9/integrations/2/validate")).andExpect(status().isOk());
        mvc.perform(put("/api/workspaces/9/integrations/2/enabled").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"expectedVersion\":1,\"sharingConfirmed\":false}")).andExpect(status().isOk());
        for(String endpoint:List.of("discoveries","sync-runs"))
            mvc.perform(post("/api/workspaces/9/integrations/2/"+endpoint).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"requestKey\":\"unique\"}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/workspaces/9/integrations/2/imports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestKey\":\"unique\",\"items\":[{\"repositoryId\":4,\"projectId\":null,\"status\":\"PLANNING\"}],\"sharingConfirmed\":false}"))
                .andExpect(status().isAccepted());
        verify(service).trigger(eq(9L),eq(2L),eq("IMPORT"),eq("unique"),any());
        mvc.perform(post("/api/workspaces/9/projects/20/repositories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryId\":4,\"sharingConfirmed\":false}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/workspaces/9/projects/20/repositories/4")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/workspaces/9/integrations/2")).andExpect(status().isNoContent());
        verify(service).delete(9L,2L);verify(service).unbind(9L,20L,4L);
    }
    @Test void concurrentConfigurationUsesStableConflictCode() throws Exception {
        when(service.enabled(eq(9L),eq(2L),any())).thenThrow(new IntegrationConflictException("连接配置已变化"));
        mvc.perform(put("/api/workspaces/9/integrations/2/enabled").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"expectedVersion\":0,\"sharingConfirmed\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTEGRATION_CONFLICT"));
    }
}
