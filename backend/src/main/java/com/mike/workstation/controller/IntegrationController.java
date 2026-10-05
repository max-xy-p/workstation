package com.mike.workstation.controller;

import com.mike.workstation.domain.DevelopmentKind;
import com.mike.workstation.dto.*;
import com.mike.workstation.service.IntegrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 数据源业务入口，权限与事务由服务层集中处理。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class IntegrationController {
    private final IntegrationService service;
    public IntegrationController(IntegrationService service) { this.service=service; }
    @GetMapping("/integrations")
    public PageResponse<IntegrationConnectionResponse> list(@PathVariable Long workspaceId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(workspaceId,page,size);
    }
    @PostMapping("/integrations") @ResponseStatus(HttpStatus.CREATED)
    public IntegrationConnectionResponse create(@PathVariable Long workspaceId,@RequestBody IntegrationConnectionRequest request) {
        return service.save(workspaceId,null,request);
    }
    @PatchMapping("/integrations/{connectionId}")
    public IntegrationConnectionResponse update(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestBody IntegrationConnectionRequest request) { return service.save(workspaceId,connectionId,request); }
    @PostMapping("/integrations/{connectionId}/validate")
    public IntegrationConnectionResponse validate(@PathVariable Long workspaceId,@PathVariable Long connectionId) {
        return service.validate(workspaceId,connectionId);
    }
    @PutMapping("/integrations/{connectionId}/enabled")
    public IntegrationConnectionResponse enabled(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestBody IntegrationEnableRequest request) { return service.enabled(workspaceId,connectionId,request); }
    @DeleteMapping("/integrations/{connectionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId,@PathVariable Long connectionId) { service.delete(workspaceId,connectionId); }
    @PostMapping("/integrations/{connectionId}/discoveries") @ResponseStatus(HttpStatus.ACCEPTED)
    public IntegrationRunResponse discover(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestBody IntegrationTriggerRequest r) { return service.trigger(workspaceId,connectionId,"DISCOVER",r.requestKey(),null); }
    @PostMapping("/integrations/{connectionId}/sync-runs") @ResponseStatus(HttpStatus.ACCEPTED)
    public IntegrationRunResponse sync(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestBody IntegrationTriggerRequest r) { return service.trigger(workspaceId,connectionId,"SYNC",r.requestKey(),null); }
    @PostMapping("/integrations/{connectionId}/imports") @ResponseStatus(HttpStatus.ACCEPTED)
    public IntegrationRunResponse imports(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestBody IntegrationImportRequest r) { return service.trigger(workspaceId,connectionId,"IMPORT",r.requestKey(),r); }
    @GetMapping("/integrations/{connectionId}/repositories")
    public PageResponse<RepositoryResponse> repositories(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.repositories(workspaceId,connectionId,null,page,size);
    }
    @GetMapping("/integrations/{connectionId}/sync-runs")
    public PageResponse<IntegrationRunResponse> runs(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.runs(workspaceId,connectionId,page,size);
    }
    @GetMapping("/integrations/{connectionId}/sync-runs/{runId}")
    public IntegrationRunResponse run(@PathVariable Long workspaceId,@PathVariable Long connectionId,@PathVariable Long runId) {
        return service.run(workspaceId,connectionId,runId);
    }
    @GetMapping("/integrations/{connectionId}/sync-runs/{runId}/items")
    public PageResponse<IntegrationRunItemResponse> items(@PathVariable Long workspaceId,@PathVariable Long connectionId,
            @PathVariable Long runId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.items(workspaceId,connectionId,runId,page,size);
    }
    @GetMapping("/projects/{projectId}/repositories")
    public PageResponse<RepositoryResponse> projectRepositories(@PathVariable Long workspaceId,@PathVariable Long projectId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.repositories(workspaceId,null,projectId,page,size);
    }
    @PostMapping("/projects/{projectId}/repositories") @ResponseStatus(HttpStatus.CREATED)
    public RepositoryResponse bind(@PathVariable Long workspaceId,@PathVariable Long projectId,@RequestBody RepositoryBindRequest r) {
        return service.bind(workspaceId,projectId,r);
    }
    @DeleteMapping("/projects/{projectId}/repositories/{repositoryId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbind(@PathVariable Long workspaceId,@PathVariable Long projectId,@PathVariable Long repositoryId) {
        service.unbind(workspaceId,projectId,repositoryId);
    }
    @GetMapping("/projects/{projectId}/development")
    public ProjectDevelopmentResponse development(@PathVariable Long workspaceId,@PathVariable Long projectId) {
        return service.development(workspaceId,projectId);
    }
    @GetMapping("/projects/{projectId}/development/items")
    public PageResponse<DevelopmentItemResponse> developmentItems(@PathVariable Long workspaceId,@PathVariable Long projectId,
            @RequestParam DevelopmentKind kind,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.developmentItems(workspaceId,projectId,kind,page,size);
    }
    @GetMapping("/development/repositories")
    public PageResponse<RepositoryResponse> workspaceDevelopment(@PathVariable Long workspaceId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.repositories(workspaceId,null,-1L,page,size);
    }
}
