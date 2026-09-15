import type { components, operations } from './generated/schema.js';

type Schemas = components['schemas'];
type ResourceId = number;

export type { components, operations, paths } from './generated/schema.js';
export type ApiErrorResponse = Schemas['ApiError'];
export type DashboardResponse = Schemas['DashboardResponse'];
export type NoteRequest = Schemas['NoteRequest'];
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
  return {
    dashboardApi: {
      summary: () => transport.get<DashboardResponse>('/dashboard')
    },
    projectApi: {
      list: (params?: ProjectListParams) =>
        transport.get<ProjectPageResponse>('/projects', params),
      listAll: (params?: ProjectListParams) =>
        listAllPages<ProjectSummaryResponse, ProjectListParams>(transport, '/projects', params),
      detail: (id: ResourceId) => transport.get<ProjectDetailResponse>(`/projects/${id}`),
      create: (payload: ProjectRequest) =>
        transport.post<ProjectResponse, ProjectRequest>('/projects', payload),
      update: (id: ResourceId, payload: ProjectRequest) =>
        transport.put<ProjectResponse, ProjectRequest>(`/projects/${id}`, payload),
      updateStatus: (id: ResourceId, status: ProjectStatus) =>
        transport.patch<ProjectResponse, Schemas['ProjectStatusRequest']>(
          `/projects/${id}/status`,
          { status }
        ),
      delete: (id: ResourceId) => transport.delete(`/projects/${id}`)
    },
    taskApi: {
      list: (params?: TaskListParams) => transport.get<TaskPageResponse>('/tasks', params),
      listAll: (params?: TaskListParams) =>
        listAllPages<TaskResponse, TaskListParams>(transport, '/tasks', params),
      get: (id: ResourceId) => transport.get<TaskResponse>(`/tasks/${id}`),
      create: (payload: TaskRequest) =>
        transport.post<TaskResponse, TaskRequest>('/tasks', payload),
      update: (id: ResourceId, payload: TaskRequest) =>
        transport.put<TaskResponse, TaskRequest>(`/tasks/${id}`, payload),
      updateStatus: (id: ResourceId, status: TaskStatus) =>
        transport.patch<TaskResponse, Schemas['TaskStatusRequest']>(`/tasks/${id}/status`, {
          status
        }),
      delete: (id: ResourceId) => transport.delete(`/tasks/${id}`)
    },
    noteApi: {
      list: (params?: NoteListParams) => transport.get<NotePageResponse>('/notes', params),
      listAll: (params?: NoteListParams) =>
        listAllPages<NoteSummaryResponse, NoteListParams>(transport, '/notes', params),
      get: (id: ResourceId) => transport.get<NoteDetailResponse>(`/notes/${id}`),
      create: (payload: NoteRequest) =>
        transport.post<NoteDetailResponse, NoteRequest>('/notes', payload),
      update: (id: ResourceId, payload: NoteRequest) =>
        transport.put<NoteDetailResponse, NoteRequest>(`/notes/${id}`, payload),
      importMarkdown: (payload: NoteImportRequest) =>
        transport.post<NoteImportResponse, NoteImportRequest>('/notes/import', payload),
      delete: (id: ResourceId) => transport.delete(`/notes/${id}`)
    },
    noteFolderApi: {
      list: () => transport.get<NoteFolderResponse[]>('/note-folders'),
      get: (id: ResourceId) => transport.get<NoteFolderResponse>(`/note-folders/${id}`),
      create: (payload: NoteFolderRequest) =>
        transport.post<NoteFolderResponse, NoteFolderRequest>('/note-folders', payload),
      update: (id: ResourceId, payload: NoteFolderRequest) =>
        transport.put<NoteFolderResponse, NoteFolderRequest>(`/note-folders/${id}`, payload),
      delete: (id: ResourceId) => transport.delete(`/note-folders/${id}`)
    },
    tagApi: {
      list: () => transport.get<TagResponse[]>('/tags'),
      get: (id: ResourceId) => transport.get<TagResponse>(`/tags/${id}`),
      create: (payload: TagRequest) =>
        transport.post<TagResponse, TagRequest>('/tags', payload),
      update: (id: ResourceId, payload: TagRequest) =>
        transport.put<TagResponse, TagRequest>(`/tags/${id}`, payload),
      delete: (id: ResourceId) => transport.delete(`/tags/${id}`)
    }
  };
}

export type WorkstationApi = ReturnType<typeof createWorkstationApi>;

export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
export type { FetchTransportOptions, TokenProvider } from './fetch-transport.js';
