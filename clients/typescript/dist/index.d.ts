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
export type WorkspacePermission = Schemas['WorkspacePermission'];
export type WorkspaceRole = Schemas['WorkspaceRole'];
export type AuditEventResponse = Schemas['AuditEventResponse'];
export type AuditEventPageResponse = Schemas['AuditEventPageResponse'];
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
export type AuditEventListParams = NonNullable<operations['listAuditEvents']['parameters']['query']>;
export type QueryParams = object;
export interface WorkstationTransport {
    get<T>(path: string, params?: QueryParams): Promise<T>;
    post<T, B>(path: string, body: B): Promise<T>;
    put<T, B>(path: string, body: B): Promise<T>;
    patch<T, B>(path: string, body: B): Promise<T>;
    delete<T = null>(path: string): Promise<T>;
}
export declare function createWorkstationApi(transport: WorkstationTransport): {
    workspaceApi: {
        list: () => Promise<{
            id: number;
            name: string;
            type: components["schemas"]["WorkspaceType"];
            role: components["schemas"]["WorkspaceRole"];
            permissions: components["schemas"]["WorkspacePermission"][];
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: ResourceId) => Promise<{
            id: number;
            name: string;
            type: components["schemas"]["WorkspaceType"];
            role: components["schemas"]["WorkspaceRole"];
            permissions: components["schemas"]["WorkspacePermission"][];
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: ResourceId, payload: WorkspaceRequest) => Promise<{
            id: number;
            name: string;
            type: components["schemas"]["WorkspaceType"];
            role: components["schemas"]["WorkspaceRole"];
            permissions: components["schemas"]["WorkspacePermission"][];
            createdAt: string;
            updatedAt: string;
        }>;
        listMembers: (workspaceId: ResourceId) => Promise<{
            subjectId: string;
            principalType: components["schemas"]["PrincipalType"];
            username: string | null;
            displayName: string | null;
            role: components["schemas"]["WorkspaceRole"];
            joinedAt: string;
        }[]>;
        addMember: (workspaceId: ResourceId, payload: WorkspaceMemberRequest) => Promise<{
            subjectId: string;
            principalType: components["schemas"]["PrincipalType"];
            username: string | null;
            displayName: string | null;
            role: components["schemas"]["WorkspaceRole"];
            joinedAt: string;
        }>;
        updateMember: (workspaceId: ResourceId, subjectId: string, role: WorkspaceRole) => Promise<{
            subjectId: string;
            principalType: components["schemas"]["PrincipalType"];
            username: string | null;
            displayName: string | null;
            role: components["schemas"]["WorkspaceRole"];
            joinedAt: string;
        }>;
        removeMember: (workspaceId: ResourceId, subjectId: string) => Promise<null>;
    };
    auditApi: {
        list: (workspaceId: ResourceId, params?: AuditEventListParams) => Promise<{
            items: components["schemas"]["AuditEventResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
    };
    dashboardApi: {
        summary: (workspaceId: ResourceId) => Promise<{
            activeProjectCount: number;
            todoTaskCount: number;
            inProgressTaskCount: number;
            recentProjects: components["schemas"]["ProjectSummaryResponse"][];
            recentNotes: components["schemas"]["NoteSummaryResponse"][];
        }>;
    };
    projectApi: {
        list: (workspaceId: ResourceId, params?: ProjectListParams) => Promise<{
            items: components["schemas"]["ProjectSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (workspaceId: ResourceId, params?: ProjectListParams) => Promise<{
            id: number;
            name: string;
            description: string | null;
            status: components["schemas"]["ProjectStatus"];
            repositoryUrl: string | null;
            techStack: string | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        detail: (workspaceId: ResourceId, id: ResourceId) => Promise<{
            project: components["schemas"]["ProjectResponse"];
            tasks: components["schemas"]["TaskResponse"][];
            notes: components["schemas"]["NoteSummaryResponse"][];
        }>;
        create: (workspaceId: ResourceId, payload: ProjectRequest) => Promise<{
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
        update: (workspaceId: ResourceId, id: ResourceId, payload: ProjectRequest) => Promise<{
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
        updateStatus: (workspaceId: ResourceId, id: ResourceId, status: ProjectStatus) => Promise<{
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
        delete: (workspaceId: ResourceId, id: ResourceId) => Promise<null>;
    };
    taskApi: {
        list: (workspaceId: ResourceId, params?: TaskListParams) => Promise<{
            items: components["schemas"]["TaskResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (workspaceId: ResourceId, params?: TaskListParams) => Promise<{
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
        get: (workspaceId: ResourceId, id: ResourceId) => Promise<{
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
        create: (workspaceId: ResourceId, payload: TaskRequest) => Promise<{
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
        update: (workspaceId: ResourceId, id: ResourceId, payload: TaskRequest) => Promise<{
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
        updateStatus: (workspaceId: ResourceId, id: ResourceId, status: TaskStatus) => Promise<{
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
        delete: (workspaceId: ResourceId, id: ResourceId) => Promise<null>;
    };
    noteApi: {
        list: (workspaceId: ResourceId, params?: NoteListParams) => Promise<{
            items: components["schemas"]["NoteSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (workspaceId: ResourceId, params?: NoteListParams) => Promise<{
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
        get: (workspaceId: ResourceId, id: ResourceId) => Promise<{
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
        create: (workspaceId: ResourceId, payload: NoteRequest) => Promise<{
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
        update: (workspaceId: ResourceId, id: ResourceId, payload: NoteRequest) => Promise<{
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
        importMarkdown: (workspaceId: ResourceId, payload: NoteImportRequest) => Promise<{
            importedCount: number;
            skippedCount: number;
            failedCount: number;
            items: components["schemas"]["NoteImportItemResponse"][];
        }>;
        delete: (workspaceId: ResourceId, id: ResourceId) => Promise<null>;
    };
    noteFolderApi: {
        list: (workspaceId: ResourceId) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: ResourceId, id: ResourceId) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (workspaceId: ResourceId, payload: NoteFolderRequest) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: ResourceId, id: ResourceId, payload: NoteFolderRequest) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (workspaceId: ResourceId, id: ResourceId) => Promise<null>;
    };
    tagApi: {
        list: (workspaceId: ResourceId) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: ResourceId, id: ResourceId) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (workspaceId: ResourceId, payload: TagRequest) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: ResourceId, id: ResourceId, payload: TagRequest) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (workspaceId: ResourceId, id: ResourceId) => Promise<null>;
    };
};
export type WorkstationApi = ReturnType<typeof createWorkstationApi>;
export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
export type { FetchTransportOptions, TokenProvider } from './fetch-transport.js';
