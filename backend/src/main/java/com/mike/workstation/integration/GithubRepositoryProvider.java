package com.mike.workstation.integration;

import com.mike.workstation.domain.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** GitHub 仓库、Issue、PR、默认分支提交及 Actions 工作流适配。 */
@Component
public class GithubRepositoryProvider implements RepositoryProvider {
    private static final String LOGIN = "login";
    private static final String HTML_URL = "html_url";
    private final RemoteApi api;
    public GithubRepositoryProvider(RemoteApi api) { this.api = api; }
    @Override public String provider() { return "GITHUB"; }
    @Override public String account(IntegrationConnection c,String token) {
        return PlatformData.text(api.get(c,token,"/user"),LOGIN);
    }
    @Override public List<ExternalRepository> repositories(IntegrationConnection c,String token,int page) {
        String affiliation = "OWNED".equals(c.getScope()) ? "owner" : "owner,collaborator,organization_member";
        JsonNode response = api.get(c,token,"/user/repos?affiliation="+affiliation+"&per_page=100&page="+page);
        return response.valueStream().map(this::parse).toList();
    }
    @Override public ExternalRepository repository(IntegrationConnection c,String token,ExternalRepository r) {
        return parse(api.get(c,token,"/repositories/"+RemoteApi.encode(r.getExternalId())));
    }
    private ExternalRepository parse(JsonNode n) {
        ExternalRepository r = new ExternalRepository();
        r.setExternalId(PlatformData.text(n,"id")); r.setNamespace(PlatformData.text(n.path("owner"),LOGIN));
        r.setName(PlatformData.text(n,"name")); r.setUrl(PlatformData.text(n,HTML_URL));
        r.setDescription(PlatformData.text(n,"description")); r.setDefaultBranch(PlatformData.text(n,"default_branch"));
        r.setLanguage(PlatformData.text(n,"language")); r.setVisibility(n.path("private").asBoolean()?"private":"public");
        r.setArchived(n.path("archived").asBoolean()); r.setFork(n.path("fork").asBoolean());
        r.setLastPushedAt(PlatformData.time(n,"pushed_at")); return r;
    }
    @Override public List<DevelopmentItem> development(IntegrationConnection c,String token,ExternalRepository r,
            DevelopmentKind kind,LocalDateTime since,int page) {
        if ((kind==DevelopmentKind.COMMIT || kind==DevelopmentKind.PIPELINE) &&
                (r.getDefaultBranch()==null || r.getDefaultBranch().isBlank())) return List.of();
        String prefix="/repos/"+RemoteApi.encode(r.getNamespace())+"/"+RemoteApi.encode(r.getName());
        String when=RemoteApi.encode(since.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        String suffix=switch(kind) {
            case ISSUE -> "/issues?state=all&sort=updated&direction=desc&since="+when;
            case CHANGE_REQUEST -> "/pulls?state=all&sort=updated&direction=desc";
            case COMMIT -> "/commits?sha="+RemoteApi.encode(r.getDefaultBranch())+"&since="+when;
            case PIPELINE -> "/actions/runs?branch="+RemoteApi.encode(r.getDefaultBranch())+"&created="+RemoteApi.encode(">="+since.atZone(ZoneId.systemDefault()).toOffsetDateTime());
        };
        JsonNode data=api.get(c,token,prefix+suffix+"&per_page=100&page="+page);
        if (kind==DevelopmentKind.PIPELINE) data=data.path("workflow_runs");
        List<DevelopmentItem> items=new ArrayList<>();
        for (JsonNode n:data) {
            DevelopmentItem i=parseItem(n,kind,r);
            // 被排除的 PR 仍作为页占位，分页由原页大小决定。
            if (kind==DevelopmentKind.ISSUE && n.has("pull_request")) i.setKind("CHANGE_REQUEST");
            items.add(i);
        }
        return items;
    }
    @Override public DevelopmentItem issue(IntegrationConnection c,String token,ExternalRepository r,long number) {
        JsonNode data=api.get(c,token,"/repos/"+RemoteApi.encode(r.getNamespace())+"/"+RemoteApi.encode(r.getName())+"/issues/"+number);
        if(data.has("pull_request")) throw new com.mike.workstation.exception.BusinessException("来源已不再是可关联的 Issue");
        return parseItem(data,DevelopmentKind.ISSUE,r);
    }
    private DevelopmentItem parseItem(JsonNode n,DevelopmentKind kind,ExternalRepository r) {
        if(kind==DevelopmentKind.COMMIT) {
            JsonNode commit=n.path("commit");
            DevelopmentItem i=new DevelopmentItem();
            i.setKind(kind.name()); i.setExternalId(PlatformData.text(n,"sha"));
            i.setTitle(commit.path("message").asText().lines().findFirst().orElse(""));
            i.setUrl(PlatformData.text(n,HTML_URL)); i.setStatus("COMMITTED");
            i.setAuthor(PlatformData.text(commit.path("author"),"name")); i.setBranch(r.getDefaultBranch());
            i.setSourceUpdatedAt(PlatformData.time(commit.path("committer"),"date")); return i;
        }
        DevelopmentItem i=PlatformData.item(n,kind.name(),"id",kind==DevelopmentKind.PIPELINE?"name":"title",HTML_URL,"updated_at");
        String raw=PlatformData.text(n,kind==DevelopmentKind.PIPELINE?"conclusion":"state");
        if(kind==DevelopmentKind.PIPELINE && raw==null) raw=PlatformData.text(n,"status");
        if(i.getTitle()==null) i.setTitle("");
        if(i.getTitle().length()>1000) i.setTitle(i.getTitle().substring(0,1000));
        i.setRawStatus(raw);
        if(kind==DevelopmentKind.ISSUE) {
            i.setIssueNumber(n.path("number").isIntegralNumber()?n.path("number").asLong():null);
            i.setStateReason(PlatformData.text(n,"state_reason"));
        }
        i.setStatus(itemStatus(n,kind,raw));
        i.setAuthor(PlatformData.text(n.path(kind==DevelopmentKind.PIPELINE?"actor":"user"),LOGIN));
        i.setBranch(kind==DevelopmentKind.PIPELINE?PlatformData.text(n,"head_branch"):null);
        return i;
    }
    private String itemStatus(JsonNode node, DevelopmentKind kind, String raw) {
        if (kind == DevelopmentKind.PIPELINE) return PlatformData.pipelineState(raw);
        if (PlatformData.text(node,"merged_at") != null) return "MERGED";
        return "open".equals(raw) ? "OPEN" : "CLOSED";
    }
}
