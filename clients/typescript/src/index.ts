import type { components, operations } from './generated/schema.js';

type Schemas = components['schemas'];
type ResourceId = number;

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
export type NoteUpdateRequest = Schemas['NoteUpdateRequest'];
export type NoteSummaryResponse = Schemas['NoteSummaryResponse'];
export type NoteDetailResponse = Schemas['NoteDetailResponse'];
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
export type TaskRequest = Schemas['TaskRequest'];
export type TaskResponse = Schemas['TaskResponse'];
export type TaskPageResponse = Schemas['TaskPageResponse'];
export type TaskStatus = Schemas['TaskStatus'];
export type TagRequest = Schemas['TagRequest'];
export type TagResponse = Schemas['TagResponse'];

export type NoteListParams = NonNullable<operations['listNotes']['parameters']['query']>;
export type ProjectListParams = NonNullable<operations['listProjects']['parameters']['query']>;
export type TaskListParams = NonNullable<operations['listTasks']['parameters']['query']>;
export type AuditEventListParams = NonNullable<operations['listAuditEvents']['parameters']['query']>;
export type QueryParams = object;

export interface WorkstationTransport {
  get<T>(path: string, params?: QueryParams): Promise<T>;
  post<T, B>(path: string, body: B): Promise<T>;
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
  let page = 0;
  let response: PageResponse<T>;
  do {
    response = await transport.get<PageResponse<T>>(path, { ...params, page, size: 100 });
    items.push(...response.items);
    page += 1;
  } while (response.hasMore);
  return items;
}

export function createWorkstationApi(transport: WorkstationTransport) {
  const workspacePath = (workspaceId: ResourceId) => `/workspaces/${workspaceId}`;
  return {
    workspaceApi: {
      list: () => transport.get<WorkspaceResponse[]>('/workspaces'),
      create: (payload: WorkspaceRequest) =>
        transport.post<WorkspaceResponse, WorkspaceRequest>('/workspaces', payload),
      get: (workspaceId: ResourceId) =>
        transport.get<WorkspaceResponse>(workspacePath(workspaceId)),
      update: (workspaceId: ResourceId, payload: WorkspaceRequest) =>
        transport.put<WorkspaceResponse, WorkspaceRequest>(workspacePath(workspaceId), payload),
      listMembers: (workspaceId: ResourceId) =>
        transport.get<WorkspaceMemberResponse[]>(`${workspacePath(workspaceId)}/members`),
      findMemberCandidates: (workspaceId: ResourceId, username: string) =>
        transport.get<WorkspaceMemberCandidate[]>(`${workspacePath(workspaceId)}/member-candidates`, { username }),
      addMember: (workspaceId: ResourceId, payload: WorkspaceMemberRequest) =>
        transport.post<WorkspaceMemberResponse, WorkspaceMemberRequest>(
          `${workspacePath(workspaceId)}/members`, payload),
      updateMember: (
        workspaceId: ResourceId,
        subjectId: string,
        role: WorkspaceRole
      ) => transport.put<WorkspaceMemberResponse, WorkspaceMemberRoleRequest>(
        `${workspacePath(workspaceId)}/members/${encodeURIComponent(subjectId)}`,
        { role }
      ),
      removeMember: (workspaceId: ResourceId, subjectId: string) =>
        transport.delete(`${workspacePath(workspaceId)}/members/${encodeURIComponent(subjectId)}`)
    },
    auditApi: {
      list: (workspaceId: ResourceId, params?: AuditEventListParams) =>
        transport.get<AuditEventPageResponse>(`${workspacePath(workspaceId)}/audit-events`, params)
    },
    dashboardApi: {
      summary: (workspaceId: ResourceId) =>
        transport.get<DashboardResponse>(`${workspacePath(workspaceId)}/dashboard`)
    },
    projectApi: {
      list: (workspaceId: ResourceId, params?: ProjectListParams) =>
        transport.get<ProjectPageResponse>(`${workspacePath(workspaceId)}/projects`, params),
      listAll: (workspaceId: ResourceId, params?: ProjectListParams) =>
        listAllPages<ProjectSummaryResponse, ProjectListParams>(
          transport, `${workspacePath(workspaceId)}/projects`, params),
      detail: (workspaceId: ResourceId, id: ResourceId) =>
        transport.get<ProjectDetailResponse>(`${workspacePath(workspaceId)}/projects/${id}`),
      create: (workspaceId: ResourceId, payload: ProjectRequest) =>
        transport.post<ProjectResponse, ProjectRequest>(`${workspacePath(workspaceId)}/projects`, payload),
      update: (workspaceId: ResourceId, id: ResourceId, payload: ProjectRequest) =>
        transport.put<ProjectResponse, ProjectRequest>(`${workspacePath(workspaceId)}/projects/${id}`, payload),
      updateStatus: (workspaceId: ResourceId, id: ResourceId, status: ProjectStatus) =>
        transport.patch<ProjectResponse, Schemas['ProjectStatusRequest']>(
          `${workspacePath(workspaceId)}/projects/${id}/status`,
          { status }
        ),
      delete: (workspaceId: ResourceId, id: ResourceId) =>
        transport.delete(`${workspacePath(workspaceId)}/projects/${id}`)
    },
    taskApi: {
      list: (workspaceId: ResourceId, params?: TaskListParams) =>
        transport.get<TaskPageResponse>(`${workspacePath(workspaceId)}/tasks`, params),
      listAll: (workspaceId: ResourceId, params?: TaskListParams) =>
        listAllPages<TaskResponse, TaskListParams>(transport, `${workspacePath(workspaceId)}/tasks`, params),
      get: (workspaceId: ResourceId, id: ResourceId) =>
        transport.get<TaskResponse>(`${workspacePath(workspaceId)}/tasks/${id}`),
      create: (workspaceId: ResourceId, payload: TaskRequest) =>
        transport.post<TaskResponse, TaskRequest>(`${workspacePath(workspaceId)}/tasks`, payload),
      update: (workspaceId: ResourceId, id: ResourceId, payload: TaskRequest) =>
        transport.put<TaskResponse, TaskRequest>(`${workspacePath(workspaceId)}/tasks/${id}`, payload),
      updateStatus: (workspaceId: ResourceId, id: ResourceId, status: TaskStatus) =>
        transport.patch<TaskResponse, Schemas['TaskStatusRequest']>(`${workspacePath(workspaceId)}/tasks/${id}/status`, {
          status
        }),
      delete: (workspaceId: ResourceId, id: ResourceId) =>
        transport.delete(`${workspacePath(workspaceId)}/tasks/${id}`)
    },
    noteApi: {
      list: (workspaceId: ResourceId, params?: NoteListParams) =>
        transport.get<NotePageResponse>(`${workspacePath(workspaceId)}/notes`, params),
      listAll: (workspaceId: ResourceId, params?: NoteListParams) =>
        listAllPages<NoteSummaryResponse, NoteListParams>(
          transport, `${workspacePath(workspaceId)}/notes`, params),
      get: (workspaceId: ResourceId, id: ResourceId) =>
        transport.get<NoteDetailResponse>(`${workspacePath(workspaceId)}/notes/${id}`),
      create: (workspaceId: ResourceId, payload: NoteRequest) =>
        transport.post<NoteDetailResponse, NoteRequest>(`${workspacePath(workspaceId)}/notes`, payload),
      update: (workspaceId: ResourceId, id: ResourceId, payload: NoteUpdateRequest) =>
        transport.put<NoteDetailResponse, NoteUpdateRequest>(`${workspacePath(workspaceId)}/notes/${id}`, payload),
      importMarkdown: (workspaceId: ResourceId, payload: NoteImportRequest) =>
        transport.post<NoteImportResponse, NoteImportRequest>(
          `${workspacePath(workspaceId)}/notes/import`, payload),
      delete: (workspaceId: ResourceId, id: ResourceId) =>
        transport.delete(`${workspacePath(workspaceId)}/notes/${id}`)
    },
    noteFolderApi: {
      list: (workspaceId: ResourceId) =>
        transport.get<NoteFolderResponse[]>(`${workspacePath(workspaceId)}/note-folders`),
      get: (workspaceId: ResourceId, id: ResourceId) =>
        transport.get<NoteFolderResponse>(`${workspacePath(workspaceId)}/note-folders/${id}`),
      create: (workspaceId: ResourceId, payload: NoteFolderRequest) =>
        transport.post<NoteFolderResponse, NoteFolderRequest>(
          `${workspacePath(workspaceId)}/note-folders`, payload),
      update: (workspaceId: ResourceId, id: ResourceId, payload: NoteFolderRequest) =>
        transport.put<NoteFolderResponse, NoteFolderRequest>(
          `${workspacePath(workspaceId)}/note-folders/${id}`, payload),
      delete: (workspaceId: ResourceId, id: ResourceId) =>
        transport.delete(`${workspacePath(workspaceId)}/note-folders/${id}`)
    },
    tagApi: {
      list: (workspaceId: ResourceId) =>
        transport.get<TagResponse[]>(`${workspacePath(workspaceId)}/tags`),
      get: (workspaceId: ResourceId, id: ResourceId) =>
        transport.get<TagResponse>(`${workspacePath(workspaceId)}/tags/${id}`),
      create: (workspaceId: ResourceId, payload: TagRequest) =>
        transport.post<TagResponse, TagRequest>(`${workspacePath(workspaceId)}/tags`, payload),
      update: (workspaceId: ResourceId, id: ResourceId, payload: TagRequest) =>
        transport.put<TagResponse, TagRequest>(`${workspacePath(workspaceId)}/tags/${id}`, payload),
      delete: (workspaceId: ResourceId, id: ResourceId) =>
        transport.delete(`${workspacePath(workspaceId)}/tags/${id}`)
    }
  };
}

export type WorkstationApi = ReturnType<typeof createWorkstationApi>;

export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
export type { FetchTransportOptions, TokenProvider } from './fetch-transport.js';
