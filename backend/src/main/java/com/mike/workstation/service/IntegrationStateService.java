package com.mike.workstation.service;

import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.IntegrationConflictException;
import com.mike.workstation.integration.CredentialCipher;
import com.mike.workstation.mapper.*;
import com.mike.workstation.security.CurrentPrincipalResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 外部同步的短事务边界，连接锁防止配置更新与旧任务提交交错。 */
@Service
public class IntegrationStateService {
    private final IntegrationMapper mapper;
    private final ProjectMapper projects;
    private final WorkspaceMapper workspaces;
    private final WorkspaceAuthorizationService authorization;
    private final CurrentPrincipalResolver principals;
    private final CredentialCipher cipher;
    private final IntegrationProperties properties;
    private final AuditService audit;

    public IntegrationStateService(IntegrationMapper mapper, ProjectMapper projects, WorkspaceMapper workspaces,
            WorkspaceAuthorizationService authorization, CurrentPrincipalResolver principals, CredentialCipher cipher,
            IntegrationProperties properties, AuditService audit) {
        this.mapper=mapper; this.projects=projects; this.workspaces=workspaces; this.authorization=authorization;
        this.principals=principals; this.cipher=cipher; this.properties=properties; this.audit=audit;
    }
    public void requireProject(Long w,Long project) {
        RequestChecks.requiredExisting(projects.findById(w,project),"项目不存在");
    }
    private IntegrationConnection locked(Long w,Long id) {
        IntegrationConnection c=mapper.lockConnection(w,id);
        if(c==null || c.isDeleted()) throw new com.mike.workstation.exception.NotFoundException("数据源连接不存在");
        return c;
    }
    private void sharing(Long w,Boolean confirmed) {
        Workspace workspace=authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE);
        if(workspace.getType()==WorkspaceType.SHARED && !Boolean.TRUE.equals(confirmed))
            throw new BusinessException("请确认导入的数据将对工作空间成员可见");
    }
    @Transactional
    public IntegrationConnection saveConnection(Long w,Long id,IntegrationConnectionRequest r,IntegrationConnection prepared,String token) {
        sharing(w,r.sharingConfirmed());
        if(id!=null) {
            IntegrationConnection current=locked(w,id);
            if(r.expectedVersion()==null || r.expectedVersion()!=current.getVersion())
                throw new IntegrationConflictException("连接配置已变化，请刷新后重试");
            mapper.cancel(w,id);
            prepared.setVersion(current.getVersion()+1);
        } else prepared.setVersion(1);
        if(id==null || (r.token()!=null && !r.token().isBlank())) prepared.setOwnerSubject(principals.require().subjectId());
        prepared.setCredential(cipher.encrypt(token,IntegrationService.context(prepared)));
        prepared.setKeyVersion(properties.keyVersion()); prepared.setName(r.name().trim());
        prepared.setScope(r.scope().name()); prepared.setIncludeForks(r.includeForks()); prepared.setIncludeArchived(r.includeArchived());
        prepared.setAutoImport(r.autoImport()); prepared.setScheduleMinutes(r.scheduleMinutes()); prepared.setEnabled(true);
        prepared.setNextSyncAt(LocalDateTime.now().plusMinutes(r.scheduleMinutes())); prepared.setLastError(null);
        if(id==null) mapper.insertConnection(prepared); else mapper.updateConnection(prepared);
        audit.success(w,"INTEGRATION_SAVE","INTEGRATION",prepared.getId()); return prepared;
    }
    @Transactional
    public IntegrationConnection enabled(Long w,Long id,IntegrationEnableRequest r) {
        sharing(w,r.sharingConfirmed()); IntegrationConnection c=locked(w,id);
        if(r.expectedVersion()==null || r.expectedVersion()!=c.getVersion() || r.enabled()==null)
            throw new IntegrationConflictException("连接配置已变化，请刷新后重试");
        if(r.enabled() && !canManage(c)) throw new BusinessException("原连接授权已失效，请重新配置凭证");
        c.setEnabled(r.enabled()); c.setVersion(c.getVersion()+1); mapper.cancel(w,id);
        c.setNextSyncAt(LocalDateTime.now()); mapper.updateConnection(c);
        audit.success(w,"INTEGRATION_ENABLE","INTEGRATION",id); return c;
    }
    @Transactional
    public void delete(Long w,Long id) {
        authorization.require(w,WorkspacePermission.INTEGRATION_MANAGE); locked(w,id);
        mapper.cancel(w,id); mapper.detachConnection(w,id); mapper.deleteConnection(w,id);
        audit.success(w,"INTEGRATION_DELETE","INTEGRATION",id);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("运行环境缺少 SHA-256",ex); }
    }
    @Transactional
    public IntegrationRun enqueue(Long w,Long id,String kind,String key,IntegrationImportRequest request) {
        authorization.require(w,WorkspacePermission.INTEGRATION_SYNC);
        RequestChecks.requiredText(key,"请求标识");
        if(!key.matches("[A-Za-z0-9_-]{1,80}")) throw new BusinessException("请求标识格式不合法");
        IntegrationConnection c=locked(w,id);
        if(!c.isEnabled() || !canManage(c)) throw new BusinessException("连接未启用或原授权已失效");
        if(request!=null) {
            sharing(w,request.sharingConfirmed()); authorization.require(w,WorkspacePermission.PROJECT_WRITE);
            if(request.items()==null || request.items().isEmpty() || request.items().size()>100)
                throw new BusinessException("每次请选择 1 到 100 个仓库");
            if(request.items().stream().map(IntegrationImportItemRequest::repositoryId).distinct().count()!=request.items().size())
                throw new BusinessException("导入仓库不能重复");
            for(var i:request.items()) {
                RequestChecks.requiredObject(i.status(),"项目状态");
                ExternalRepository repo=mapper.repository(w,i.repositoryId());
                RequestChecks.requiredExisting(repo,"仓库不存在");
                if(!id.equals(repo.getConnectionId())) throw new BusinessException("仓库不属于本次连接");
                if(i.projectId()!=null) requireProject(w,i.projectId());
            }
        }
        String actor=principals.require().subjectId();
        String digest=hash(kind+":"+c.getVersion()+":"+(request==null?"":request.items().toString()));
        IntegrationRun existing=mapper.request(w,id,actor,key);
        if(existing!=null) {
            if(!digest.equals(existing.getRequestHash())) throw new IntegrationConflictException("相同请求标识不能提交不同内容");
            return existing;
        }
        if(mapper.active(w,id)!=null) throw new IntegrationConflictException("该连接已有同步任务，请等待结束");
        IntegrationRun run=newRun(c,kind,key,digest,actor);
        mapper.insertRun(run);
        if(request!=null) for(var selection:request.items()) {
            IntegrationRunItem item=new IntegrationRunItem(); item.setWorkspaceId(w); item.setRunId(run.getId());
            item.setRepositoryId(selection.repositoryId()); item.setProjectId(selection.projectId());
            item.setProjectStatus(selection.status().name()); mapper.insertItem(item);
        }
        audit.success(w,"INTEGRATION_"+kind,"SYNC_RUN",run.getId()); return run;
    }

    private IntegrationRun newRun(IntegrationConnection c,String kind,String key,String digest,String actor) {
        IntegrationRun r=new IntegrationRun(); r.setWorkspaceId(c.getWorkspaceId()); r.setConnectionId(c.getId());
        r.setKind(kind); r.setStatus("QUEUED"); r.setRequestKey(key); r.setRequestHash(digest); r.setActorSubject(actor);
        r.setConfigVersion(c.getVersion()); r.setCreatedAt(LocalDateTime.now()); return r;
    }
    public boolean canManage(IntegrationConnection c) { return manages(c.getWorkspaceId(),c.getOwnerSubject()); }
    private boolean manages(Long w,String actor) {
        WorkspaceMember member=workspaces.findMember(w,actor);
        return member!=null && (member.getRole()==WorkspaceRole.OWNER || member.getRole()==WorkspaceRole.ADMIN);
    }
    @Transactional
    public void schedule(Long w,Long id) {
        IntegrationConnection c=locked(w,id);
        if(!c.isEnabled() || c.getScheduleMinutes()<=0 || c.getNextSyncAt()==null || c.getNextSyncAt().isAfter(LocalDateTime.now())) return;
        if(!canManage(c)) { c.setEnabled(false); c.setVersion(c.getVersion()+1); mapper.cancel(w,id); mapper.updateConnection(c); return; }
        if(mapper.active(w,id)==null) mapper.insertRun(newRun(c,"SYNC",UUID.randomUUID().toString(),hash("SCHEDULED"),c.getOwnerSubject()));
        c.setNextSyncAt(LocalDateTime.now().plusMinutes(c.getScheduleMinutes())); mapper.updateConnection(c);
    }
    @Transactional
    public IntegrationRun claim() {
        IntegrationRun r=mapper.nextRun(); if(r==null) return null;
        r.setStatus("RUNNING"); r.setLeaseToken(UUID.randomUUID().toString());
        r.setLeaseUntil(LocalDateTime.now().plusSeconds(properties.leaseSeconds())); mapper.claim(r); return r;
    }
    private IntegrationConnection check(IntegrationRun run) {
        IntegrationConnection c=locked(run.getWorkspaceId(),run.getConnectionId());
        if(!c.isEnabled() || c.getVersion()!=run.getConfigVersion() || !canManage(c) ||
                !manages(c.getWorkspaceId(),run.getActorSubject()) || mapper.lockRun(run)==null)
            throw new IntegrationConflictException("同步任务已取消或租约已失效");
        run.setLeaseUntil(LocalDateTime.now().plusSeconds(properties.leaseSeconds()));
        return c;
    }
    @Transactional
    public void heartbeat(IntegrationRun run) { check(run); mapper.updateRun(run); }

    @Transactional
    public void saveRepositories(IntegrationRun run,List<ExternalRepository> repositories) {
        saveRepositoryBatch(run,repositories,false);
    }
    @Transactional
    public void refreshRepository(IntegrationRun run,ExternalRepository repository) {
        saveRepositoryBatch(run,List.of(repository),true);
    }
    private void saveRepositoryBatch(IntegrationRun run,List<ExternalRepository> repositories,boolean selected) {
        IntegrationConnection c=check(run);
        for(ExternalRepository r:repositories) {
            if(!selected && ((r.isFork() && !c.isIncludeForks()) || (r.isArchived() && !c.isIncludeArchived()))) continue;
            r.setWorkspaceId(c.getWorkspaceId()); r.setProvider(c.getProvider()); r.setBaseUrl(c.getBaseUrl());
            r.setConnectionId(c.getId()); r.setLastSyncedAt(LocalDateTime.now());
            ExternalRepository old=mapper.source(r);
            if(old==null) mapper.insertRepository(r);
            else {
                if(old.getConnectionId()!=null && !c.getId().equals(old.getConnectionId())) continue;
                r.setId(old.getId()); mapper.updateRepository(r);
            }
            if(c.isAutoImport()) autoImport(c,r);
        }
        run.setProcessed(run.getProcessed()+repositories.size()); mapper.updateRun(run);
    }
    private void autoImport(IntegrationConnection c,ExternalRepository r) {
        mapper.lockRepository(c.getWorkspaceId(),r.getId());
        ExternalRepository stored=mapper.repository(c.getWorkspaceId(),r.getId());
        if(stored.getProjectId()==null && !stored.isAutoImportSuppressed()) {
            Project p=createProject(c.getWorkspaceId(),r,ProjectStatus.PLANNING);
            mapper.bind(c.getWorkspaceId(),p.getId(),r.getId());
            auditBackground(c.getWorkspaceId(),c.getOwnerSubject(),"PROJECT_IMPORT","PROJECT",p.getId());
        }
    }
    private void auditBackground(Long w,String actor,String action,String type,Long id) {
        SecurityPrincipal principal=workspaces.findPrincipal(actor);
        audit.background(w,actor,principal==null?PrincipalType.USER:principal.getPrincipalType(),action,type,id);
    }
    private Project createProject(Long w,ExternalRepository r,ProjectStatus status) {
        Project p=new Project(); p.setWorkspaceId(w);
        p.setName(r.getName().substring(0,Math.min(r.getName().length(),200))); p.setDescription(r.getDescription());
        p.setStatus(status); p.setCreatedAt(LocalDateTime.now()); p.setUpdatedAt(p.getCreatedAt());
        projects.insert(p); return p;
    }
    @Transactional
    public void importItem(IntegrationRun run,IntegrationRunItem item) {
        check(run); mapper.lockRepository(run.getWorkspaceId(),item.getRepositoryId());
        ExternalRepository r=mapper.repository(run.getWorkspaceId(),item.getRepositoryId());
        RequestChecks.requiredExisting(r,"仓库不存在");
        if(!run.getConnectionId().equals(r.getConnectionId())) throw new BusinessException("仓库连接已变化");
        if(r.getProjectId()!=null && item.getProjectId()!=null && !r.getProjectId().equals(item.getProjectId()))
            throw new IntegrationConflictException("仓库已经关联其他项目");
        Long project=r.getProjectId()!=null?r.getProjectId():item.getProjectId();
        if(project==null) project=createProject(run.getWorkspaceId(),r,ProjectStatus.valueOf(item.getProjectStatus())).getId();
        else requireProject(run.getWorkspaceId(),project);
        if(r.getProjectId()==null) mapper.bind(run.getWorkspaceId(),project,r.getId());
        item.setProjectId(project); item.setStatus("SUCCEEDED"); item.setLastError(null); mapper.updateItem(item);
        run.setProcessed(run.getProcessed()+1); mapper.updateRun(run);
        auditBackground(run.getWorkspaceId(),run.getActorSubject(),"PROJECT_IMPORT","PROJECT",project);
    }
    @Transactional
    public void failedItem(IntegrationRun run,IntegrationRunItem item,String error) {
        check(run); item.setStatus("FAILED"); item.setLastError(error); mapper.updateItem(item);
        run.setFailed(run.getFailed()+1); mapper.updateRun(run);
    }
    @Transactional
    public void repositoryError(IntegrationRun run,Long repository,String error) {
        check(run); mapper.repositoryError(run.getWorkspaceId(),repository,error);
        run.setFailed(run.getFailed()+1); mapper.updateRun(run);
    }
    @Transactional
    public void snapshot(IntegrationRun run,ExternalRepository r,DevelopmentSnapshot snapshot,List<DevelopmentItem> items) {
        check(run);
        snapshot.setWorkspaceId(run.getWorkspaceId()); snapshot.setRepositoryId(r.getId()); snapshot.setSyncedAt(LocalDateTime.now());
        if(snapshot.isAvailable()) {
            mapper.clearDevelopment(run.getWorkspaceId(),r.getId(),snapshot.getKind());
            for(var i:items) {
                i.setWorkspaceId(run.getWorkspaceId()); i.setRepositoryId(r.getId());
                if(i.getTitle()==null) i.setTitle("");
                if(i.getTitle().length()>1000) i.setTitle(i.getTitle().substring(0,1000));
                mapper.insertDevelopment(i);
            }
        }
        mapper.snapshot(snapshot); mapper.updateRun(run);
    }
    @Transactional
    public void finish(IntegrationRun run,String status,String error) {
        IntegrationConnection c=mapper.lockConnection(run.getWorkspaceId(),run.getConnectionId());
        if(c==null || mapper.lockRun(run)==null) return;
        if(c.isDeleted() || !c.isEnabled() || c.getVersion()!=run.getConfigVersion() || !canManage(c) || !manages(c.getWorkspaceId(),run.getActorSubject())) status="CANCELLED";
        run.setStatus(status); run.setLastError(error); run.setFinishedAt(LocalDateTime.now()); mapper.updateRun(run);
        if(!"CANCELLED".equals(status)) mapper.completed(c.getWorkspaceId(),c.getId(),run.getFinishedAt(),error);
        auditBackground(run.getWorkspaceId(),run.getActorSubject(),"SYNC_"+status,"SYNC_RUN",run.getId());
    }
    @Transactional
    public ExternalRepository bind(Long w,Long project,RepositoryBindRequest request) {
        authorization.require(w,WorkspacePermission.PROJECT_WRITE); sharing(w,request.sharingConfirmed()); requireProject(w,project);
        mapper.lockRepository(w,request.repositoryId()); ExternalRepository r=mapper.repository(w,request.repositoryId());
        RequestChecks.requiredExisting(r,"仓库不存在");
        if(r.getProjectId()!=null && !project.equals(r.getProjectId())) throw new IntegrationConflictException("仓库已经关联其他项目");
        if(r.getProjectId()==null) mapper.bind(w,project,r.getId());
        r.setProjectId(project); audit.success(w,"REPOSITORY_BIND","PROJECT",project); return r;
    }
    @Transactional
    public void unbind(Long w,Long project,Long repository) {
        authorization.require(w,WorkspacePermission.PROJECT_WRITE); requireProject(w,project);
        mapper.suppressRepository(w,project,repository);
        mapper.unbind(w,project,repository); audit.success(w,"REPOSITORY_UNBIND","PROJECT",project);
    }
}
