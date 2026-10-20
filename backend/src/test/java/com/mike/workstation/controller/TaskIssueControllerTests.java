package com.mike.workstation.controller;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.exception.*;
import com.mike.workstation.service.TaskIssueService;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TaskIssueControllerTests {
    private final TaskIssueService service=mock(TaskIssueService.class);
    private MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.standaloneSetup(new TaskIssueController(service)).setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @Test void createDistinguishesNewAndExisting() throws Exception {
        when(service.create(eq(9L),eq(20L),any())).thenReturn(new IssueTaskResult(null,true),new IssueTaskResult(null,false));
        String body="""
                {"developmentItemId":1,"sharingConfirmed":true,"task":{"title":"任务","projectId":20,"status":"TODO","priority":"HIGH"}}
                """;
        mvc.perform(post("/api/workspaces/9/projects/20/issue-tasks").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.created").value(true));
        mvc.perform(post("/api/workspaces/9/projects/20/issue-tasks").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false));
    }
    @Test void readAndUnlinkUseScopedPaths() throws Exception {
        mvc.perform(get("/api/workspaces/9/issue-sources").param("projectId","20")).andExpect(status().isOk());verify(service).list(9L,20L,0,20);mvc.perform(delete("/api/workspaces/9/tasks/5/issue-source")).andExpect(status().isNoContent());verify(service).unlink(9L,5L);
    }
    @Test void linkConflictHasExplicitCode() throws Exception {
        when(service.link(eq(9L),eq(20L),any())).thenThrow(new TaskIssueConflictException("任务已有来源"));
        mvc.perform(post("/api/workspaces/9/projects/20/issue-links").contentType(MediaType.APPLICATION_JSON).content("""
                {"developmentItemId":1,"sharingConfirmed":true,"taskId":5}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_ISSUE_CONFLICT"));
    }
}
