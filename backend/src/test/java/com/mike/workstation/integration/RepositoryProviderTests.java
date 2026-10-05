package com.mike.workstation.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RepositoryProviderTests {
    private final RemoteApi remote=mock(RemoteApi.class);
    private final ObjectMapper json=new ObjectMapper();
    private final IntegrationConnection connection=new IntegrationConnection();
    private final LocalDateTime since=LocalDateTime.of(2026,9,1,0,0);
    private ExternalRepository repo() {
        ExternalRepository r=new ExternalRepository(); r.setExternalId("12");r.setNamespace("owner");r.setName("repo");r.setDefaultBranch("main");return r;
    }
    private void response(String data) { when(remote.get(any(),eq("token"),anyString())).thenReturn(json.readTree(data)); }
    @Test void githubDiscoversAccountsAndRepositoriesByScope() {
        GithubRepositoryProvider p=new GithubRepositoryProvider(remote);
        response("{\"login\":\"owner\"}"); assertThat(p.account(connection,"token")).isEqualTo("owner");
        connection.setScope("OWNED");
        response("[{\"id\":12,\"owner\":{\"login\":\"owner\"},\"name\":\"repo\",\"html_url\":\"https://github.com/owner/repo\",\"private\":true,\"pushed_at\":\"2026-09-01T12:00:00Z\"}]");
        assertThat(p.repositories(connection,"token",1)).singleElement().satisfies(r->{
            assertThat(r.getExternalId()).isEqualTo("12");assertThat(r.getVisibility()).isEqualTo("private");
            assertThat(r.getLastPushedAt()).isNotNull();
        });
        verify(remote).get(connection,"token","/user/repos?affiliation=owner&per_page=100&page=1");
        connection.setScope("ACCESSIBLE");p.repositories(connection,"token",2);
        verify(remote).get(connection,"token","/user/repos?affiliation=owner,collaborator,organization_member&per_page=100&page=2");
        response("{\"id\":12,\"owner\":{\"login\":\"owner\"},\"name\":\"renamed\"}");
        assertThat(p.repository(connection,"token",repo()).getName()).isEqualTo("renamed");
        assertThat(p.provider()).isEqualTo("GITHUB");
    }
    @Test void githubKeepsPullRequestsOutOfIssuesAndRecognizesMerge() {
        GithubRepositoryProvider p=new GithubRepositoryProvider(remote);
        response("[{\"id\":1,\"title\":\"issue\",\"state\":\"open\",\"updated_at\":\"2026-09-20T00:00:00Z\"},{\"id\":2,\"title\":\"pull\",\"state\":\"closed\",\"pull_request\":{}}]");
        List<DevelopmentItem> issues=p.development(connection,"token",repo(),DevelopmentKind.ISSUE,since,1);
        assertThat(issues).extracting(DevelopmentItem::getKind).containsExactly("ISSUE","CHANGE_REQUEST");
        assertThat(issues.get(0).getStatus()).isEqualTo("OPEN");
        response("[{\"id\":2,\"title\":\"pull\",\"state\":\"closed\",\"merged_at\":\"2026-09-20T00:00:00Z\"}]");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.CHANGE_REQUEST,since,1).get(0).getStatus()).isEqualTo("MERGED");
    }
    @Test void githubMapsDefaultBranchCommitsAndPipelineConclusions() {
        GithubRepositoryProvider p=new GithubRepositoryProvider(remote);
        response("[{\"sha\":\"abc\",\"html_url\":\"https://github.com/x/commit/abc\",\"commit\":{\"message\":\"标题\\n正文\",\"author\":{\"name\":\"author\"},\"committer\":{\"date\":\"2026-09-20T00:00:00Z\"}}}]");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.COMMIT,since,1)).singleElement().satisfies(i->{
            assertThat(i.getTitle()).isEqualTo("标题");assertThat(i.getBranch()).isEqualTo("main");assertThat(i.getStatus()).isEqualTo("COMMITTED");
        });
        response("{\"workflow_runs\":[{\"id\":1,\"name\":\"CI\",\"status\":\"completed\",\"conclusion\":\"failure\",\"head_branch\":\"main\"},{\"id\":2,\"name\":\"CI\",\"status\":\"in_progress\",\"conclusion\":null}]}");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.PIPELINE,since,1))
                .extracting(DevelopmentItem::getStatus).containsExactly("FAILED","RUNNING");
        ExternalRepository empty=repo();empty.setDefaultBranch(null);
        assertThat(p.development(connection,"token",empty,DevelopmentKind.COMMIT,since,1)).isEmpty();
    }
    @Test void gitlabUsesInstanceProjectIdentityAndScope() {
        GitlabRepositoryProvider p=new GitlabRepositoryProvider(remote);
        response("{\"username\":\"person\"}");assertThat(p.account(connection,"token")).isEqualTo("person");
        response("[{\"id\":12,\"name\":\"repo\",\"namespace\":{\"full_path\":\"team/sub\"},\"visibility\":\"internal\",\"forked_from_project\":{},\"archived\":true}]");
        connection.setScope("OWNED");
        assertThat(p.repositories(connection,"token",1)).singleElement().satisfies(r->{
            assertThat(r.getNamespace()).isEqualTo("team/sub");assertThat(r.isFork()).isTrue();assertThat(r.isArchived()).isTrue();
            assertThat(r.getLastPushedAt()).isNull();
        });
        verify(remote).get(connection,"token","/projects?owned=true&per_page=100&page=1");
        connection.setScope("ACCESSIBLE");p.repositories(connection,"token",2);
        verify(remote).get(connection,"token","/projects?membership=true&per_page=100&page=2");
        response("{\"id\":12,\"name\":\"renamed\",\"namespace\":{\"full_path\":\"team\"}}");
        assertThat(p.repository(connection,"token",repo()).getName()).isEqualTo("renamed");
        assertThat(p.provider()).isEqualTo("GITLAB");
    }
    @Test void gitlabNormalizesIssuesMergeRequestsCommitsAndPipelines() {
        GitlabRepositoryProvider p=new GitlabRepositoryProvider(remote);
        response("[{\"id\":1,\"title\":\"issue\",\"state\":\"opened\",\"author\":{\"username\":\"x\"}}]");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.ISSUE,since,1).get(0).getStatus()).isEqualTo("OPEN");
        response("[{\"id\":1,\"title\":\"mr\",\"state\":\"merged\"},{\"id\":2,\"title\":\"mr\",\"state\":\"closed\"}]");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.CHANGE_REQUEST,since,1))
                .extracting(DevelopmentItem::getStatus).containsExactly("MERGED","CLOSED");
        response("[{\"id\":\"abc\",\"title\":\"commit\",\"author_name\":\"x\",\"committed_date\":\"2026-09-20T00:00:00Z\"}]");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.COMMIT,since,1).get(0).getStatus()).isEqualTo("COMMITTED");
        response("[{\"id\":1,\"status\":\"success\",\"ref\":\"main\"},{\"id\":2,\"status\":\"canceled\"}]");
        assertThat(p.development(connection,"token",repo(),DevelopmentKind.PIPELINE,since,1))
                .extracting(DevelopmentItem::getStatus).containsExactly("SUCCESS","CANCELLED");
        ExternalRepository empty=repo();empty.setDefaultBranch("");
        assertThat(p.development(connection,"token",empty,DevelopmentKind.PIPELINE,since,1)).isEmpty();
    }
    @Test void pipelineStatePreservesUnknownAndPendingOutcomes() {
        for(String raw:List.of("queued","pending","manual","requested","waiting")) assertThat(PlatformData.pipelineState(raw)).isEqualTo("PENDING");
        for(String raw:List.of("failed","failure","timed_out")) assertThat(PlatformData.pipelineState(raw)).isEqualTo("FAILED");
        assertThat(PlatformData.pipelineState("success")).isEqualTo("SUCCESS");
        assertThat(PlatformData.pipelineState("skipped")).isEqualTo("SKIPPED");
        assertThat(PlatformData.pipelineState("neutral")).isEqualTo("NEUTRAL");
        assertThat(PlatformData.pipelineState("new_state")).isEqualTo("UNKNOWN");
        assertThat(PlatformData.pipelineState(null)).isEqualTo("UNKNOWN");
    }
}
