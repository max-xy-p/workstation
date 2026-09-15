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
export declare function createWorkstationApi(transport: WorkstationTransport): {
    dashboardApi: {
        summary: () => Promise<{
            activeProjectCount: number;
            todoTaskCount: number;
            inProgressTaskCount: number;
            recentProjects: components["schemas"]["ProjectSummaryResponse"][];
            recentNotes: components["schemas"]["NoteSummaryResponse"][];
        }>;
    };
    projectApi: {
        list: (params?: ProjectListParams) => Promise<{
            items: components["schemas"]["ProjectSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (params?: ProjectListParams) => Promise<{
            id: number;
            name: string;
            description: string | null;
            status: components["schemas"]["ProjectStatus"];
            repositoryUrl: string | null;
            techStack: string | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        detail: (id: ResourceId) => Promise<{
            project: components["schemas"]["ProjectResponse"];
            tasks: components["schemas"]["TaskResponse"][];
            notes: components["schemas"]["NoteSummaryResponse"][];
        }>;
        create: (payload: ProjectRequest) => Promise<{
            id: number;
            name: string;
            description: string | null;
            status: components["schemas"]["ProjectStatus"];
            repositoryUrl: string | null;
            techStack: string | null;
            runGuide: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (id: ResourceId, payload: ProjectRequest) => Promise<{
            id: number;
            name: string;
            description: string | null;
            status: components["schemas"]["ProjectStatus"];
            repositoryUrl: string | null;
            techStack: string | null;
            runGuide: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        updateStatus: (id: ResourceId, status: ProjectStatus) => Promise<{
            id: number;
            name: string;
            description: string | null;
            status: components["schemas"]["ProjectStatus"];
            repositoryUrl: string | null;
            techStack: string | null;
            runGuide: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (id: ResourceId) => Promise<null>;
    };
    taskApi: {
        list: (params?: TaskListParams) => Promise<{
            items: components["schemas"]["TaskResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (params?: TaskListParams) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (id: ResourceId) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (payload: TaskRequest) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (id: ResourceId, payload: TaskRequest) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        updateStatus: (id: ResourceId, status: TaskStatus) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (id: ResourceId) => Promise<null>;
    };
    noteApi: {
        list: (params?: NoteListParams) => Promise<{
            items: components["schemas"]["NoteSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (params?: NoteListParams) => Promise<{
            id: number;
            title: string;
            folderId: number | null;
            folderName: string | null;
            projectId: number | null;
            projectName: string | null;
            version: number;
            tags: components["schemas"]["TagResponse"][];
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (id: ResourceId) => Promise<{
            id: number;
            title: string;
            content: string;
            folderId: number | null;
            folderName: string | null;
            projectId: number | null;
            projectName: string | null;
            version: number;
            tags: components["schemas"]["TagResponse"][];
            createdAt: string;
            updatedAt: string;
        }>;
        create: (payload: NoteRequest) => Promise<{
            id: number;
            title: string;
            content: string;
            folderId: number | null;
            folderName: string | null;
            projectId: number | null;
            projectName: string | null;
            version: number;
            tags: components["schemas"]["TagResponse"][];
            createdAt: string;
            updatedAt: string;
        }>;
        update: (id: ResourceId, payload: NoteRequest) => Promise<{
            id: number;
            title: string;
            content: string;
            folderId: number | null;
            folderName: string | null;
            projectId: number | null;
            projectName: string | null;
            version: number;
            tags: components["schemas"]["TagResponse"][];
            createdAt: string;
            updatedAt: string;
        }>;
        importMarkdown: (payload: NoteImportRequest) => Promise<{
            importedCount: number;
            skippedCount: number;
            failedCount: number;
            items: components["schemas"]["NoteImportItemResponse"][];
        }>;
        delete: (id: ResourceId) => Promise<null>;
    };
    noteFolderApi: {
        list: () => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (id: ResourceId) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (payload: NoteFolderRequest) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (id: ResourceId, payload: NoteFolderRequest) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (id: ResourceId) => Promise<null>;
    };
    tagApi: {
        list: () => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (id: ResourceId) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (payload: TagRequest) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (id: ResourceId, payload: TagRequest) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (id: ResourceId) => Promise<null>;
    };
};
export type WorkstationApi = ReturnType<typeof createWorkstationApi>;
export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
export type { FetchTransportOptions, TokenProvider } from './fetch-transport.js';
