package com.mike.workstation.integration;

import com.mike.workstation.domain.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** GitLab 公共及部署允许实例的仓库与开发情况适配。 */
@Component
public class GitlabRepositoryProvider implements RepositoryProvider {
    private final RemoteApi api;
    public GitlabRepositoryProvider(RemoteApi api) { this.api=api; }
    @Override public String provider() { return "GITLAB"; }
    @Override public String account(IntegrationConnection c,String token) {
        return PlatformData.text(api.get(c,token,"/user"),"username");
    }
    @Override public List<ExternalRepository> repositories(IntegrationConnection c,String token,int page) {
        String scope="OWNED".equals(c.getScope())?"owned=true":"membership=true";
        JsonNode data=api.get(c,token,"/projects?"+scope+"&per_page=100&page="+page);
        return data.valueStream().map(this::parse).toList();
    }
    @Override public ExternalRepository repository(IntegrationConnection c,String token,ExternalRepository r) {
        return parse(api.get(c,token,"/projects/"+RemoteApi.encode(r.getExternalId())));
    }
    private ExternalRepository parse(JsonNode n) {
        ExternalRepository r=new ExternalRepository();
        r.setExternalId(PlatformData.text(n,"id")); r.setNamespace(PlatformData.text(n.path("namespace"),"full_path"));
        r.setName(PlatformData.text(n,"name")); r.setUrl(PlatformData.text(n,"web_url"));
        r.setDescription(PlatformData.text(n,"description")); r.setDefaultBranch(PlatformData.text(n,"default_branch"));
        r.setVisibility(PlatformData.text(n,"visibility")); r.setArchived(n.path("archived").asBoolean());
        r.setFork(n.has("forked_from_project")); return r;
    }
    @Override public List<DevelopmentItem> development(IntegrationConnection c,String token,ExternalRepository r,
            DevelopmentKind kind,LocalDateTime since,int page) {
        if((kind==DevelopmentKind.COMMIT || kind==DevelopmentKind.PIPELINE) &&
                (r.getDefaultBranch()==null || r.getDefaultBranch().isBlank())) return List.of();
        String when=RemoteApi.encode(since.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        String path="/projects/"+RemoteApi.encode(r.getExternalId())+switch(kind) {
            case ISSUE -> "/issues?scope=all&state=all&order_by=updated_at&sort=desc&updated_after="+when;
            case CHANGE_REQUEST -> "/merge_requests?scope=all&state=all&order_by=updated_at&sort=desc&updated_after="+when;
            case COMMIT -> "/repository/commits?ref_name="+RemoteApi.encode(r.getDefaultBranch())+"&since="+when;
            case PIPELINE -> "/pipelines?ref="+RemoteApi.encode(r.getDefaultBranch())+"&updated_after="+when+"&order_by=updated_at&sort=desc";
        };
        JsonNode data=api.get(c,token,path+"&per_page=100&page="+page);
        return data.valueStream().map(n -> parseItem(n,kind,r)).toList();
    }
    private DevelopmentItem parseItem(JsonNode node, DevelopmentKind kind, ExternalRepository repository) {
        String titleKey = kind == DevelopmentKind.PIPELINE ? "id" : "title";
        String timeKey = kind == DevelopmentKind.COMMIT ? "committed_date" : "updated_at";
        DevelopmentItem item = PlatformData.item(node,kind.name(),"id",titleKey,"web_url",timeKey);
        String raw = PlatformData.text(node,kind == DevelopmentKind.PIPELINE ? "status" : "state");
        item.setRawStatus(raw);
        item.setStatus(itemStatus(kind,raw));
        item.setAuthor(kind == DevelopmentKind.COMMIT ? PlatformData.text(node,"author_name")
                : PlatformData.text(node.path("author"),"username"));
        item.setBranch(switch (kind) {
            case PIPELINE -> PlatformData.text(node,"ref");
            case COMMIT -> repository.getDefaultBranch();
            default -> null;
        });
        return item;
    }
    private String itemStatus(DevelopmentKind kind, String raw) {
        if (kind == DevelopmentKind.COMMIT) return "COMMITTED";
        if (kind == DevelopmentKind.PIPELINE) return PlatformData.pipelineState(raw);
        if ("merged".equals(raw)) return "MERGED";
        return "opened".equals(raw) ? "OPEN" : "CLOSED";
    }
}
