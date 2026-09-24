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
        create: (payload: WorkspaceRequest) => Promise<{
            id: number;
            name: string;
            type: components["schemas"]["WorkspaceType"];
            role: components["schemas"]["WorkspaceRole"];
            permissions: components["schemas"]["WorkspacePermission"][];
            createdAt: string;
            updatedAt: string;
        }>;
        get: (workspaceId: number) => Promise<{
            id: number;
            name: string;
            type: components["schemas"]["WorkspaceType"];
            role: components["schemas"]["WorkspaceRole"];
            permissions: components["schemas"]["WorkspacePermission"][];
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: number, payload: WorkspaceRequest) => Promise<{
            id: number;
            name: string;
            type: components["schemas"]["WorkspaceType"];
            role: components["schemas"]["WorkspaceRole"];
            permissions: components["schemas"]["WorkspacePermission"][];
            createdAt: string;
            updatedAt: string;
        }>;
        listMembers: (workspaceId: number) => Promise<{
            subjectId: string;
            principalType: components["schemas"]["PrincipalType"];
            username: string | null;
            displayName: string | null;
            role: components["schemas"]["WorkspaceRole"];
            joinedAt: string;
        }[]>;
        findMemberCandidates: (workspaceId: number, username: string) => Promise<{
            subjectId: string;
            username: string;
            displayName: string | null;
        }[]>;
        addMember: (workspaceId: number, payload: WorkspaceMemberRequest) => Promise<{
            subjectId: string;
            principalType: components["schemas"]["PrincipalType"];
            username: string | null;
            displayName: string | null;
            role: components["schemas"]["WorkspaceRole"];
            joinedAt: string;
        }>;
        updateMember: (workspaceId: number, subjectId: string, role: WorkspaceRole) => Promise<{
            subjectId: string;
            principalType: components["schemas"]["PrincipalType"];
            username: string | null;
            displayName: string | null;
            role: components["schemas"]["WorkspaceRole"];
            joinedAt: string;
        }>;
        removeMember: (workspaceId: number, subjectId: string) => Promise<null>;
    };
    auditApi: {
        list: (workspaceId: number, params?: AuditEventListParams) => Promise<{
            items: components["schemas"]["AuditEventResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
    };
    dashboardApi: {
        summary: (workspaceId: number) => Promise<{
            activeProjectCount: number;
            todoTaskCount: number;
            inProgressTaskCount: number;
            recentProjects: components["schemas"]["ProjectSummaryResponse"][];
            recentNotes: components["schemas"]["NoteSummaryResponse"][];
        }>;
    };
    projectApi: {
        list: (workspaceId: number, params?: ProjectListParams) => Promise<{
            items: components["schemas"]["ProjectSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (workspaceId: number, params?: ProjectListParams) => Promise<{
            id: number;
            name: string;
            description: string | null;
            status: components["schemas"]["ProjectStatus"];
            repositoryUrl: string | null;
            techStack: string | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        detail: (workspaceId: number, id: number) => Promise<{
            project: components["schemas"]["ProjectResponse"];
            tasks: components["schemas"]["TaskResponse"][];
            notes: components["schemas"]["NoteSummaryResponse"][];
        }>;
        create: (workspaceId: number, payload: ProjectRequest) => Promise<{
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
        update: (workspaceId: number, id: number, payload: ProjectRequest) => Promise<{
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
        updateStatus: (workspaceId: number, id: number, status: ProjectStatus) => Promise<{
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
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
    taskApi: {
        list: (workspaceId: number, params?: TaskListParams) => Promise<{
            items: components["schemas"]["TaskResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (workspaceId: number, params?: TaskListParams) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            evidenceNoteId: number | null;
            evidenceNoteTitle: string | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: number, id: number) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            evidenceNoteId: number | null;
            evidenceNoteTitle: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (workspaceId: number, payload: TaskRequest) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            evidenceNoteId: number | null;
            evidenceNoteTitle: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: number, id: number, payload: TaskRequest) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            evidenceNoteId: number | null;
            evidenceNoteTitle: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        updateStatus: (workspaceId: number, id: number, status: TaskStatus) => Promise<{
            id: number;
            title: string;
            description: string | null;
            projectId: number;
            projectName: string;
            status: components["schemas"]["TaskStatus"];
            priority: components["schemas"]["TaskPriority"];
            dueDate: string | null;
            evidenceNoteId: number | null;
            evidenceNoteTitle: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
    noteApi: {
        list: (workspaceId: number, params?: NoteListParams) => Promise<{
            items: components["schemas"]["NoteSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (workspaceId: number, params?: NoteListParams) => Promise<{
            id: number;
            title: string;
            folderId: number | null;
            folderName: string | null;
            projectId: number | null;
            projectName: string | null;
            version: number;
            excerpt: string | null;
            tags: components["schemas"]["TagResponse"][];
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: number, id: number) => Promise<{
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
        create: (workspaceId: number, payload: NoteRequest) => Promise<{
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
        update: (workspaceId: number, id: number, payload: NoteUpdateRequest) => Promise<{
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
        importMarkdown: (workspaceId: number, payload: NoteImportRequest) => Promise<{
            importedCount: number;
            skippedCount: number;
            failedCount: number;
            items: components["schemas"]["NoteImportItemResponse"][];
        }>;
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
    noteFolderApi: {
        list: (workspaceId: number) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: number, id: number) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (workspaceId: number, payload: NoteFolderRequest) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: number, id: number, payload: NoteFolderRequest) => Promise<{
            id: number;
            name: string;
            parentId: number | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
    tagApi: {
        list: (workspaceId: number) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }[]>;
        get: (workspaceId: number, id: number) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        create: (workspaceId: number, payload: TagRequest) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        update: (workspaceId: number, id: number, payload: TagRequest) => Promise<{
            id: number;
            name: string;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
};
export type WorkstationApi = ReturnType<typeof createWorkstationApi>;
export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
export type { FetchTransportOptions, TokenProvider } from './fetch-transport.js';
