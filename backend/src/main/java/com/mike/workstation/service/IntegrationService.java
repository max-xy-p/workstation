package com.mike.workstation.service;

import com.mike.workstation.assembler.IntegrationResponseAssembler;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.IntegrationConflictException;
import com.mike.workstation.integration.*;
import com.mike.workstation.mapper.IntegrationMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 请求侧授权与平台验证；网络访问不占用数据库事务。 */
@Service
public class IntegrationService {
    private final IntegrationMapper mapper;
    private final IntegrationStateService state;
    private final WorkspaceAuthorizationService authorization;
    private final IntegrationProperties properties;
    private final RemoteApi remote;
    private final List<RepositoryProvider> providers;
    private final CredentialCipher cipher;

    public IntegrationService(IntegrationMapper mapper, IntegrationStateService state,
            WorkspaceAuthorizationService authorization, IntegrationProperties properties,
            RemoteApi remote, List<RepositoryProvider> providers, CredentialCipher cipher) {
        this.mapper=mapper; this.state=state; this.authorization=authorization; this.properties=properties;
        this.remote=remote; this.providers=providers; this.cipher=cipher;
    }

    public void checkEnabled() {
        if(!properties.enabled()) throw new BusinessException("数据源接入尚未启用，请联系部署管理员");
    }
    public RepositoryProvider provider(String name) {
        return providers.stream().filter(p->p.provider().equals(name)).findFirst()
                .orElseThrow(()->new BusinessException("不支持的数据源平台"));
    }
    public static String context(IntegrationConnection c) {
        return c.getWorkspaceId()+":"+c.getProvider()+":"+c.getBaseUrl()+":"+c.getOwnerSubject();
    }
    public IntegrationConnection require(Long w,Long id) {
        IntegrationConnection c=mapper.connection(w,id);
        RequestChecks.requiredExisting(c,"数据源连接不存在"); return c;
    }
    public PageResponse<IntegrationConnectionResponse> list(Long w,int page,int size) {
        authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE);
        PageQuery q=PageQuery.of(page,size);
        return PageResponse.of(mapper.connections(w,q.offset(),q.size()).stream()
                .map(IntegrationResponseAssembler::connection).toList(),page,size,mapper.countConnections(w));
    }

    public IntegrationConnectionResponse save(Long w,Long id,IntegrationConnectionRequest r) {
        checkEnabled(); authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE);
        RequestChecks.requiredObject(r.provider(),"平台"); RequestChecks.requiredObject(r.scope(),"仓库范围");
        RequestChecks.requiredText(r.name(),"连接名称"); RequestChecks.maxLength(r.name(),120,"连接名称");
        RequestChecks.requiredText(r.baseUrl(),"实例地址"); RequestChecks.maxLength(r.baseUrl(),500,"实例地址");
        RequestChecks.requiredObject(r.scheduleMinutes(),"同步周期");
        if(r.scheduleMinutes()<0 || r.scheduleMinutes()>10080) throw new BusinessException("同步周期必须为 0 到 10080 分钟");
        if(r.includeForks()==null || r.includeArchived()==null || r.autoImport()==null || r.sharingConfirmed()==null)
            throw new BusinessException("连接选项不能为空");
        IntegrationConnection c=id==null?new IntegrationConnection():require(w,id);
        if(id!=null && (r.expectedVersion()==null || r.expectedVersion()!=c.getVersion()))
            throw new IntegrationConflictException("连接配置已变化，请刷新后重试");
        String base=remote.normalize(r.provider(),r.baseUrl());
        if(id!=null && (!c.getProvider().equals(r.provider().name()) || !c.getBaseUrl().equals(base)))
            throw new BusinessException("平台与实例不可修改，请创建新连接");
        if(id==null) { c.setWorkspaceId(w); c.setProvider(r.provider().name()); c.setBaseUrl(base); }
        String token=r.token();
        if(token==null || token.isBlank()) {
            if(id==null) throw new BusinessException("平台凭证不能为空");
            token=cipher.decrypt(c.getCredential(),c.getKeyVersion(),context(c));
        }
        RequestChecks.maxLength(token,4096,"平台凭证");
        c.setAccount(provider(c.getProvider()).account(c,token));
        RequestChecks.requiredText(c.getAccount(),"平台账号");
        return IntegrationResponseAssembler.connection(state.saveConnection(w,id,r,c,token));
    }

    public IntegrationConnectionResponse validate(Long w,Long id) {
        checkEnabled(); authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE);
        IntegrationConnection c=require(w,id);
        String account=provider(c.getProvider()).account(c,cipher.decrypt(c.getCredential(),c.getKeyVersion(),context(c)));
        if(!account.equals(c.getAccount())) throw new BusinessException("凭证对应账号已变化，请更新连接");
        return IntegrationResponseAssembler.connection(c);
    }
    public IntegrationConnectionResponse enabled(Long w,Long id,IntegrationEnableRequest r) {
        checkEnabled(); return IntegrationResponseAssembler.connection(state.enabled(w,id,r));
    }
    public void delete(Long w,Long id) { state.delete(w,id); }

    public PageResponse<RepositoryResponse> repositories(Long w,Long c,Long project,int page,int size) {
        if(c!=null) { authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE); require(w,c); }
        else { authorization.require(w,WorkspacePermission.PROJECT_READ); if(project!=null && project>0) state.requireProject(w,project); }
        PageQuery q=PageQuery.of(page,size);
        return PageResponse.of(mapper.repositories(w,c,project,q.offset(),q.size()).stream()
                .map(IntegrationResponseAssembler::repository).toList(),page,size,mapper.countRepositories(w,c,project));
    }
    public IntegrationRunResponse trigger(Long w,Long id,String kind,String requestKey,IntegrationImportRequest r) {
        checkEnabled(); return IntegrationResponseAssembler.run(state.enqueue(w,id,kind,requestKey,r));
    }
    public PageResponse<IntegrationRunResponse> runs(Long w,Long id,int page,int size) {
        authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE); require(w,id);
        PageQuery q=PageQuery.of(page,size);
        return PageResponse.of(mapper.runs(w,id,q.offset(),q.size()).stream().map(IntegrationResponseAssembler::run).toList(),
                page,size,mapper.countRuns(w,id));
    }
    public IntegrationRunResponse run(Long w,Long c,Long id) { return IntegrationResponseAssembler.run(requireRun(w,c,id)); }
    private IntegrationRun requireRun(Long w,Long c,Long id) {
        authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE); require(w,c);
        IntegrationRun r=mapper.run(w,c,id); RequestChecks.requiredExisting(r,"同步任务不存在"); return r;
    }
    public PageResponse<IntegrationRunItemResponse> items(Long w,Long c,Long id,int page,int size) {
        requireRun(w,c,id); PageQuery q=PageQuery.of(page,size);
        return PageResponse.of(mapper.items(w,id,q.offset(),q.size()).stream().map(IntegrationResponseAssembler::item).toList(),
                page,size,mapper.countItems(w,id));
    }
    public RepositoryResponse bind(Long w,Long project,RepositoryBindRequest r) {
        return IntegrationResponseAssembler.repository(state.bind(w,project,r));
    }
    public void unbind(Long w,Long project,Long repository) { state.unbind(w,project,repository); }
    public ProjectDevelopmentResponse development(Long w,Long project) {
        authorization.require(w,WorkspacePermission.PROJECT_READ); state.requireProject(w,project);
        java.util.ArrayList<RepositoryResponse> repositories = new java.util.ArrayList<>();
        List<ExternalRepository> batch;
        long offset = 0;
        do {
            batch = mapper.repositories(w,null,project,offset,100);
            repositories.addAll(batch.stream().map(IntegrationResponseAssembler::repository).toList());
            offset += batch.size();
        } while (batch.size() == 100);
        return new ProjectDevelopmentResponse(repositories,mapper.snapshots(w,project).stream()
                .map(IntegrationResponseAssembler::snapshot).toList());
    }
    public PageResponse<DevelopmentItemResponse> developmentItems(Long w,Long project,DevelopmentKind kind,int page,int size) {
        authorization.require(w,WorkspacePermission.PROJECT_READ); state.requireProject(w,project);
        PageQuery q=PageQuery.of(page,size);
        return PageResponse.of(mapper.development(w,project,kind.name(),q.offset(),q.size()).stream()
                .map(IntegrationResponseAssembler::development).toList(),page,size,mapper.countDevelopment(w,project,kind.name()));
    }
}
