import type { components, operations } from './generated/schema.js';

type Schemas = components['schemas'];

export type { components, operations, paths } from './generated/schema.js';
export type ApiErrorResponse = Schemas['ApiError'];
export type WorkspaceRequest = Schemas['WorkspaceRequest'];
export type WorkspaceResponse = Schemas['WorkspaceResponse'];
export type WorkspaceMemberRequest = Schemas['WorkspaceMemberRequest'];
export type WorkspaceMemberRoleRequest = Schemas['WorkspaceMemberRoleRequest'];
export type WorkspaceMemberResponse = Schemas['WorkspaceMemberResponse'];
export type WorkspaceMemberCandidate = Schemas['WorkspaceMemberCandidate'];
export type WorkspacePermission = Schemas['WorkspacePermission'];
export type WorkspaceRole = Schemas['WorkspaceRole'];
export type AuditEventResponse = Schemas['AuditEventResponse'];
export type AuditEventPageResponse = Schemas['AuditEventPageResponse'];
export type DashboardResponse = Schemas['DashboardResponse'];
export type NoteRequest = Schemas['NoteRequest'];
export type NoteRevisionSource = Schemas['NoteRevisionSource'];
export type NoteRevisionSummaryResponse = Schemas['NoteRevisionSummaryResponse'];
export type NoteRevisionResponse = Schemas['NoteRevisionResponse'];
export type NoteRevisionPageResponse = Schemas['NoteRevisionPageResponse'];
export type NoteRevisionRestoreRequest = Schemas['NoteRevisionRestoreRequest'];
export type NoteRevisionListParams = NonNullable<operations['listNoteRevisions']['parameters']['query']>;
export type NoteUpdateRequest = Schemas['NoteUpdateRequest'];
export type NoteSummaryResponse = Schemas['NoteSummaryResponse'];
export type NoteDetailResponse = Schemas['NoteDetailResponse'];
export type DeletedNoteResponse = Schemas['DeletedNoteResponse'];
export type DeletedNotePageResponse = Schemas['DeletedNotePageResponse'];
export type NoteImportRequest = Schemas['NoteImportRequest'];
export type NoteImportResponse = Schemas['NoteImportResponse'];
export type NoteFolderRequest = Schemas['NoteFolderRequest'];
export type NoteFolderResponse = Schemas['NoteFolderResponse'];
export type NotePageResponse = Schemas['NotePageResponse'];
export type ProjectRequest = Schemas['ProjectRequest'];
export type ProjectSummaryResponse = Schemas['ProjectSummaryResponse'];
export type ProjectResponse = Schemas['ProjectResponse'];
export type ProjectDetailResponse = Schemas['ProjectDetailResponse'];
export type ProjectPageResponse = Schemas['ProjectPageResponse'];
export type ProjectStatus = Schemas['ProjectStatus'];
export type TaskIssueSourceResponse = Schemas['TaskIssueSourceResponse'];
export type TaskIssueSourcePageResponse = Schemas['TaskIssueSourcePageResponse'];
export type IssueTaskCreateRequest = Schemas['IssueTaskCreateRequest'];
export type IssueTaskLinkRequest = Schemas['IssueTaskLinkRequest'];
export type IssueTaskResult = Schemas['IssueTaskResult'];
export type TaskRequest = Schemas['TaskRequest'];
export type TaskResponse = Schemas['TaskResponse'];
export type TaskPageResponse = Schemas['TaskPageResponse'];
export type TaskStatus = Schemas['TaskStatus'];
export type TagRequest = Schemas['TagRequest'];
export type TagResponse = Schemas['TagResponse'];

export type NoteListParams = NonNullable<operations['listNotes']['parameters']['query']>;
export type DeletedNoteListParams = NonNullable<operations['listDeletedNotes']['parameters']['query']>;
export type ProjectListParams = NonNullable<operations['listProjects']['parameters']['query']>;
export type TaskListParams = NonNullable<operations['listTasks']['parameters']['query']>;
export type AuditEventListParams = NonNullable<operations['listAuditEvents']['parameters']['query']>;
export type IntegrationProvider = Schemas['IntegrationProvider'];
export type IntegrationScope = Schemas['IntegrationScope'];
export type IntegrationConnectionRequest = Schemas['IntegrationConnectionRequest'];
export type IntegrationConnectionResponse = Schemas['IntegrationConnectionResponse'];
export type IntegrationTriggerRequest = Schemas['IntegrationTriggerRequest'];
export type IntegrationImportItemRequest = Schemas['IntegrationImportItemRequest'];
export type IntegrationImportRequest = Schemas['IntegrationImportRequest'];
export type RepositoryResponse = Schemas['RepositoryResponse'];
export type IntegrationRunResponse = Schemas['IntegrationRunResponse'];
export type IntegrationRunItemResponse = Schemas['IntegrationRunItemResponse'];
export type RepositoryBindRequest = Schemas['RepositoryBindRequest'];
export type IntegrationEnableRequest = Schemas['IntegrationEnableRequest'];
export type DevelopmentKind = Schemas['DevelopmentKind'];
export type DevelopmentSnapshotResponse = Schemas['DevelopmentSnapshotResponse'];
export type DevelopmentItemResponse = Schemas['DevelopmentItemResponse'];
export type ProjectDevelopmentResponse = Schemas['ProjectDevelopmentResponse'];
export type RepositoryPageResponse = Schemas['RepositoryPageResponse'];
export type IntegrationConnectionPageResponse = Schemas['IntegrationConnectionPageResponse'];
export type IntegrationRunPageResponse = Schemas['IntegrationRunPageResponse'];
export type IntegrationRunItemPageResponse = Schemas['IntegrationRunItemPageResponse'];
export type DevelopmentItemPageResponse = Schemas['DevelopmentItemPageResponse'];
export type QueryParams = object;

export interface WorkstationTransport {
  get<T>(path: string, params?: QueryParams): Promise<T>;
  post<T, B = undefined>(path: string, body?: B): Promise<T>;
  put<T, B>(path: string, body: B): Promise<T>;
  patch<T, B>(path: string, body: B): Promise<T>;
  delete<T = null>(path: string): Promise<T>;
}

interface PageResponse<T> {
  items: T[];
  hasMore: boolean;
}

async function listAllPages<T, P extends QueryParams>(
  transport: WorkstationTransport,
  path: string,
  params?: P
): Promise<T[]> {
  const items: T[] = [];
  // 后一页是否存在由当前响应决定，保持逐页请求，避免并发越界读取。
  async function readPage(page: number): Promise<T[]> {
    const response = await transport.get<PageResponse<T>>(path, { ...params, page, size: 100 });
    items.push(...response.items);
    if (response.hasMore) return readPage(page + 1);
    return items;
  }
  return readPage(0);
}

export function createWorkstationApi(transport: WorkstationTransport) {
  const workspacePath = (workspaceId: number) => `/workspaces/${workspaceId}`;
  return {
    integrationApi: {
      list: (w: number, page = 0, size = 20) => transport.get<IntegrationConnectionPageResponse>(`${workspacePath(w)}/integrations`, { page, size }),
      create: (w: number, body: IntegrationConnectionRequest) => transport.post<IntegrationConnectionResponse, IntegrationConnectionRequest>(`${workspacePath(w)}/integrations`, body),
      update: (w: number, id: number, body: IntegrationConnectionRequest) => transport.patch<IntegrationConnectionResponse, IntegrationConnectionRequest>(`${workspacePath(w)}/integrations/${id}`, body),
      validate: (w: number, id: number) => transport.post<IntegrationConnectionResponse>(`${workspacePath(w)}/integrations/${id}/validate`),
      enabled: (w: number, id: number, body: IntegrationEnableRequest) => transport.put<IntegrationConnectionResponse, IntegrationEnableRequest>(`${workspacePath(w)}/integrations/${id}/enabled`, body),
      delete: (w: number, id: number) => transport.delete<null>(`${workspacePath(w)}/integrations/${id}`),
      discover: (w: number, id: number, requestKey: string) => transport.post<IntegrationRunResponse, IntegrationTriggerRequest>(`${workspacePath(w)}/integrations/${id}/discoveries`, { requestKey }),
      sync: (w: number, id: number, requestKey: string) => transport.post<IntegrationRunResponse, IntegrationTriggerRequest>(`${workspacePath(w)}/integrations/${id}/sync-runs`, { requestKey }),
      imports: (w: number, id: number, body: IntegrationImportRequest) => transport.post<IntegrationRunResponse, IntegrationImportRequest>(`${workspacePath(w)}/integrations/${id}/imports`, body),
      repositories: (w: number, id: number, page = 0, size = 20) => transport.get<RepositoryPageResponse>(`${workspacePath(w)}/integrations/${id}/repositories`, { page, size }),
      runs: (w: number, id: number, page = 0, size = 20) => transport.get<IntegrationRunPageResponse>(`${workspacePath(w)}/integrations/${id}/sync-runs`, { page, size }),
      run: (w: number, id: number, runId: number) => transport.get<IntegrationRunResponse>(`${workspacePath(w)}/integrations/${id}/sync-runs/${runId}`),
      items: (w: number, id: number, runId: number, page = 0, size = 20) => transport.get<IntegrationRunItemPageResponse>(`${workspacePath(w)}/integrations/${id}/sync-runs/${runId}/items`, { page, size }),
      projectRepositories: (w: number, project: number, page = 0, size = 20) => transport.get<RepositoryPageResponse>(`${workspacePath(w)}/projects/${project}/repositories`, { page, size }),
      bind: (w: number, project: number, body: RepositoryBindRequest) => transport.post<RepositoryResponse, RepositoryBindRequest>(`${workspacePath(w)}/projects/${project}/repositories`, body),
      unbind: (w: number, project: number, repository: number) => transport.delete<null>(`${workspacePath(w)}/projects/${project}/repositories/${repository}`),
      development: (w: number, project: number) => transport.get<ProjectDevelopmentResponse>(`${workspacePath(w)}/projects/${project}/development`),
      developmentItems: (w: number, project: number, kind: DevelopmentKind, page = 0, size = 20) => transport.get<DevelopmentItemPageResponse>(`${workspacePath(w)}/projects/${project}/development/items`, { kind, page, size }),
      workspaceDevelopment: (w: number, page = 0, size = 20) => transport.get<RepositoryPageResponse>(`${workspacePath(w)}/development/repositories`, { page, size }),
    },
    workspaceApi: {
      list: () => transport.get<WorkspaceResponse[]>('/workspaces'),
      create: (payload: WorkspaceRequest) =>
        transport.post<WorkspaceResponse, WorkspaceRequest>('/workspaces', payload),
      get: (workspaceId: number) =>
        transport.get<WorkspaceResponse>(workspacePath(workspaceId)),
      update: (workspaceId: number, payload: WorkspaceRequest) =>
        transport.put<WorkspaceResponse, WorkspaceRequest>(workspacePath(workspaceId), payload),
      listMembers: (workspaceId: number) =>
        transport.get<WorkspaceMemberResponse[]>(`${workspacePath(workspaceId)}/members`),
      findMemberCandidates: (workspaceId: number, username: string) =>
        transport.get<WorkspaceMemberCandidate[]>(`${workspacePath(workspaceId)}/member-candidates`, { username }),
      addMember: (workspaceId: number, payload: WorkspaceMemberRequest) =>
        transport.post<WorkspaceMemberResponse, WorkspaceMemberRequest>(
          `${workspacePath(workspaceId)}/members`, payload),
      updateMember: (
        workspaceId: number,
        subjectId: string,
        role: WorkspaceRole
      ) => transport.put<WorkspaceMemberResponse, WorkspaceMemberRoleRequest>(
        `${workspacePath(workspaceId)}/members/${encodeURIComponent(subjectId)}`,
        { role }
      ),
      removeMember: (workspaceId: number, subjectId: string) =>
        transport.delete(`${workspacePath(workspaceId)}/members/${encodeURIComponent(subjectId)}`)
    },
    auditApi: {
      list: (workspaceId: number, params?: AuditEventListParams) =>
        transport.get<AuditEventPageResponse>(`${workspacePath(workspaceId)}/audit-events`, params)
    },
    dashboardApi: {
      summary: (workspaceId: number) =>
        transport.get<DashboardResponse>(`${workspacePath(workspaceId)}/dashboard`)
    },
    projectApi: {
      list: (workspaceId: number, params?: ProjectListParams) =>
        transport.get<ProjectPageResponse>(`${workspacePath(workspaceId)}/projects`, params),
      listAll: (workspaceId: number, params?: ProjectListParams) =>
        listAllPages<ProjectSummaryResponse, ProjectListParams>(
          transport, `${workspacePath(workspaceId)}/projects`, params),
      detail: (workspaceId: number, id: number) =>
        transport.get<ProjectDetailResponse>(`${workspacePath(workspaceId)}/projects/${id}`),
      create: (workspaceId: number, payload: ProjectRequest) =>
        transport.post<ProjectResponse, ProjectRequest>(`${workspacePath(workspaceId)}/projects`, payload),
      update: (workspaceId: number, id: number, payload: ProjectRequest) =>
        transport.put<ProjectResponse, ProjectRequest>(`${workspacePath(workspaceId)}/projects/${id}`, payload),
      updateStatus: (workspaceId: number, id: number, status: ProjectStatus) =>
        transport.patch<ProjectResponse, Schemas['ProjectStatusRequest']>(
          `${workspacePath(workspaceId)}/projects/${id}/status`,
          { status }
        ),
      delete: (workspaceId: number, id: number) =>
        transport.delete(`${workspacePath(workspaceId)}/projects/${id}`)
    },
    issueApi: {
      list: (w: number, projectId?: number, page = 0, size = 20) => transport.get<TaskIssueSourcePageResponse>(`${workspacePath(w)}/issue-sources`, { projectId, page, size }),
      listAll: (w: number, projectId?: number) => listAllPages<TaskIssueSourceResponse, { projectId?: number }>(transport, `${workspacePath(w)}/issue-sources`, { projectId }),
      create: (w: number, project: number, body: IssueTaskCreateRequest) => transport.post<IssueTaskResult, IssueTaskCreateRequest>(`${workspacePath(w)}/projects/${project}/issue-tasks`, body),
      link: (w: number, project: number, body: IssueTaskLinkRequest) => transport.post<IssueTaskResult, IssueTaskLinkRequest>(`${workspacePath(w)}/projects/${project}/issue-links`, body),
      unlink: (w: number, task: number) => transport.delete<null>(`${workspacePath(w)}/tasks/${task}/issue-source`),
    },
    taskApi: {
      list: (workspaceId: number, params?: TaskListParams) =>
        transport.get<TaskPageResponse>(`${workspacePath(workspaceId)}/tasks`, params),
      listAll: (workspaceId: number, params?: TaskListParams) =>
        listAllPages<TaskResponse, TaskListParams>(transport, `${workspacePath(workspaceId)}/tasks`, params),
      get: (workspaceId: number, id: number) =>
        transport.get<TaskResponse>(`${workspacePath(workspaceId)}/tasks/${id}`),
      create: (workspaceId: number, payload: TaskRequest) =>
        transport.post<TaskResponse, TaskRequest>(`${workspacePath(workspaceId)}/tasks`, payload),
      update: (workspaceId: number, id: number, payload: TaskRequest) =>
        transport.put<TaskResponse, TaskRequest>(`${workspacePath(workspaceId)}/tasks/${id}`, payload),
      updateStatus: (workspaceId: number, id: number, status: TaskStatus) =>
        transport.patch<TaskResponse, Schemas['TaskStatusRequest']>(`${workspacePath(workspaceId)}/tasks/${id}/status`, {
          status
        }),
      delete: (workspaceId: number, id: number) =>
        transport.delete(`${workspacePath(workspaceId)}/tasks/${id}`)
    },
    noteApi: {
      revisions: (workspaceId: number, id: number, params?: NoteRevisionListParams) =>
        transport.get<NoteRevisionPageResponse>(`${workspacePath(workspaceId)}/notes/${id}/revisions`, params),
      revision: (workspaceId: number, id: number, revisionId: number) =>
        transport.get<NoteRevisionResponse>(`${workspacePath(workspaceId)}/notes/${id}/revisions/${revisionId}`),
      restoreRevision: (workspaceId: number, id: number, revisionId: number, expectedVersion: number) =>
        transport.post<NoteDetailResponse, NoteRevisionRestoreRequest>(
          `${workspacePath(workspaceId)}/notes/${id}/revisions/${revisionId}/restore`, { expectedVersion }),
      list: (workspaceId: number, params?: NoteListParams) =>
        transport.get<NotePageResponse>(`${workspacePath(workspaceId)}/notes`, params),
      listAll: (workspaceId: number, params?: NoteListParams) =>
        listAllPages<NoteSummaryResponse, NoteListParams>(
          transport, `${workspacePath(workspaceId)}/notes`, params),
      listDeleted: (workspaceId: number, params?: DeletedNoteListParams) =>
        transport.get<DeletedNotePageResponse>(`${workspacePath(workspaceId)}/notes/trash`, params),
      get: (workspaceId: number, id: number) =>
        transport.get<NoteDetailResponse>(`${workspacePath(workspaceId)}/notes/${id}`),
      create: (workspaceId: number, payload: NoteRequest) =>
        transport.post<NoteDetailResponse, NoteRequest>(`${workspacePath(workspaceId)}/notes`, payload),
      update: (workspaceId: number, id: number, payload: NoteUpdateRequest) =>
        transport.put<NoteDetailResponse, NoteUpdateRequest>(`${workspacePath(workspaceId)}/notes/${id}`, payload),
      importMarkdown: (workspaceId: number, payload: NoteImportRequest) =>
        transport.post<NoteImportResponse, NoteImportRequest>(
          `${workspacePath(workspaceId)}/notes/import`, payload),
      delete: (workspaceId: number, id: number) =>
        transport.delete(`${workspacePath(workspaceId)}/notes/${id}`),
      restore: (workspaceId: number, id: number) =>
        transport.post<NoteDetailResponse>(`${workspacePath(workspaceId)}/notes/${id}/restore`),
      permanentlyDelete: (workspaceId: number, id: number) =>
        transport.delete(`${workspacePath(workspaceId)}/notes/${id}/permanent`)
    },
    noteFolderApi: {
      list: (workspaceId: number) =>
        transport.get<NoteFolderResponse[]>(`${workspacePath(workspaceId)}/note-folders`),
      get: (workspaceId: number, id: number) =>
        transport.get<NoteFolderResponse>(`${workspacePath(workspaceId)}/note-folders/${id}`),
      create: (workspaceId: number, payload: NoteFolderRequest) =>
        transport.post<NoteFolderResponse, NoteFolderRequest>(
          `${workspacePath(workspaceId)}/note-folders`, payload),
      update: (workspaceId: number, id: number, payload: NoteFolderRequest) =>
        transport.put<NoteFolderResponse, NoteFolderRequest>(
          `${workspacePath(workspaceId)}/note-folders/${id}`, payload),
      delete: (workspaceId: number, id: number) =>
        transport.delete(`${workspacePath(workspaceId)}/note-folders/${id}`)
    },
    tagApi: {
      list: (workspaceId: number) =>
        transport.get<TagResponse[]>(`${workspacePath(workspaceId)}/tags`),
      get: (workspaceId: number, id: number) =>
        transport.get<TagResponse>(`${workspacePath(workspaceId)}/tags/${id}`),
      create: (workspaceId: number, payload: TagRequest) =>
        transport.post<TagResponse, TagRequest>(`${workspacePath(workspaceId)}/tags`, payload),
      update: (workspaceId: number, id: number, payload: TagRequest) =>
        transport.put<TagResponse, TagRequest>(`${workspacePath(workspaceId)}/tags/${id}`, payload),
      delete: (workspaceId: number, id: number) =>
        transport.delete(`${workspacePath(workspaceId)}/tags/${id}`)
    }
  };
}

export type WorkstationApi = ReturnType<typeof createWorkstationApi>;

export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
export type { FetchTransportOptions, TokenProvider } from './fetch-transport.js';
