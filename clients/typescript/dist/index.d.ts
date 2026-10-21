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
export type IssueSourceConfirmRequest = Schemas['IssueSourceConfirmRequest'];
export type IssueSourceState = Schemas['IssueSourceState'];
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
export type GithubWebhookResponse = Schemas['GithubWebhookResponse'];
export type GithubWebhookRequest = Schemas['GithubWebhookRequest'];
export type GithubWebhookDeliveryResponse = Schemas['GithubWebhookDeliveryResponse'];
export type GithubWebhookDeliveryPageResponse = Schemas['GithubWebhookDeliveryPageResponse'];
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
export declare function createWorkstationApi(transport: WorkstationTransport): {
    webhookApi: {
        get: (w: number, id: number) => Promise<{
            configured: boolean;
            enabled: boolean;
            version: number;
            endpointPath: string | null;
        }>;
        configure: (w: number, id: number, body: GithubWebhookRequest) => Promise<{
            configured: boolean;
            enabled: boolean;
            version: number;
            endpointPath: string | null;
        }>;
        deliveries: (w: number, id: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["GithubWebhookDeliveryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        retry: (w: number, id: number, delivery: number) => Promise<{
            id: number;
            deliveryKey: string;
            eventType: string;
            action: string | null;
            status: "RECEIVED" | "QUEUED" | "RETRY" | "SUCCEEDED" | "FAILED" | "IGNORED" | "CANCELLED";
            attempts: number;
            runId: number | null;
            lastError: string | null;
            receivedAt: string;
        }>;
    };
    integrationApi: {
        list: (w: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["IntegrationConnectionResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        create: (w: number, body: IntegrationConnectionRequest) => Promise<{
            id: number;
            name: string;
            provider: components["schemas"]["IntegrationProvider"];
            baseUrl: string;
            account: string;
            scope: components["schemas"]["IntegrationScope"];
            includeForks: boolean;
            includeArchived: boolean;
            autoImport: boolean;
            scheduleMinutes: number;
            enabled: boolean;
            version: number;
            lastError: string | null;
            lastSyncedAt: string | null;
        }>;
        update: (w: number, id: number, body: IntegrationConnectionRequest) => Promise<{
            id: number;
            name: string;
            provider: components["schemas"]["IntegrationProvider"];
            baseUrl: string;
            account: string;
            scope: components["schemas"]["IntegrationScope"];
            includeForks: boolean;
            includeArchived: boolean;
            autoImport: boolean;
            scheduleMinutes: number;
            enabled: boolean;
            version: number;
            lastError: string | null;
            lastSyncedAt: string | null;
        }>;
        validate: (w: number, id: number) => Promise<{
            id: number;
            name: string;
            provider: components["schemas"]["IntegrationProvider"];
            baseUrl: string;
            account: string;
            scope: components["schemas"]["IntegrationScope"];
            includeForks: boolean;
            includeArchived: boolean;
            autoImport: boolean;
            scheduleMinutes: number;
            enabled: boolean;
            version: number;
            lastError: string | null;
            lastSyncedAt: string | null;
        }>;
        enabled: (w: number, id: number, body: IntegrationEnableRequest) => Promise<{
            id: number;
            name: string;
            provider: components["schemas"]["IntegrationProvider"];
            baseUrl: string;
            account: string;
            scope: components["schemas"]["IntegrationScope"];
            includeForks: boolean;
            includeArchived: boolean;
            autoImport: boolean;
            scheduleMinutes: number;
            enabled: boolean;
            version: number;
            lastError: string | null;
            lastSyncedAt: string | null;
        }>;
        delete: (w: number, id: number) => Promise<null>;
        discover: (w: number, id: number, requestKey: string) => Promise<{
            id: number;
            kind: "DISCOVER" | "IMPORT" | "SYNC";
            status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED" | "CANCELLED";
            processed: number;
            failed: number;
            lastError: string | null;
            createdAt: string | null;
            finishedAt: string | null;
        }>;
        sync: (w: number, id: number, requestKey: string) => Promise<{
            id: number;
            kind: "DISCOVER" | "IMPORT" | "SYNC";
            status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED" | "CANCELLED";
            processed: number;
            failed: number;
            lastError: string | null;
            createdAt: string | null;
            finishedAt: string | null;
        }>;
        imports: (w: number, id: number, body: IntegrationImportRequest) => Promise<{
            id: number;
            kind: "DISCOVER" | "IMPORT" | "SYNC";
            status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED" | "CANCELLED";
            processed: number;
            failed: number;
            lastError: string | null;
            createdAt: string | null;
            finishedAt: string | null;
        }>;
        repositories: (w: number, id: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["RepositoryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        runs: (w: number, id: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["IntegrationRunResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        run: (w: number, id: number, runId: number) => Promise<{
            id: number;
            kind: "DISCOVER" | "IMPORT" | "SYNC";
            status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED" | "CANCELLED";
            processed: number;
            failed: number;
            lastError: string | null;
            createdAt: string | null;
            finishedAt: string | null;
        }>;
        items: (w: number, id: number, runId: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["IntegrationRunItemResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        projectRepositories: (w: number, project: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["RepositoryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        bind: (w: number, project: number, body: RepositoryBindRequest) => Promise<{
            id: number;
            provider: components["schemas"]["IntegrationProvider"];
            namespace: string;
            name: string;
            url: string;
            description: string | null;
            defaultBranch: string | null;
            language: string | null;
            visibility: string;
            archived: boolean;
            projectId: number | null;
            connectionId: number | null;
            lastPushedAt: string | null;
            lastSyncedAt: string | null;
            lastError: string | null;
            collectedOpenIssues: number | null;
            collectedOpenChangeRequests: number | null;
            latestPipelineStatus: string | null;
        }>;
        unbind: (w: number, project: number, repository: number) => Promise<null>;
        development: (w: number, project: number) => Promise<{
            repositories: components["schemas"]["RepositoryResponse"][];
            snapshots: components["schemas"]["DevelopmentSnapshotResponse"][];
        }>;
        developmentItems: (w: number, project: number, kind: DevelopmentKind, page?: number, size?: number) => Promise<{
            items: components["schemas"]["DevelopmentItemResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        workspaceDevelopment: (w: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["RepositoryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
    };
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
            homepageUrl: string | null;
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
            homepageUrl: string | null;
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
            homepageUrl: string | null;
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
            homepageUrl: string | null;
            techStack: string | null;
            runGuide: string | null;
            createdAt: string;
            updatedAt: string;
        }>;
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
    issueApi: {
        confirm: (w: number, task: number, body: IssueSourceConfirmRequest) => Promise<{
            id: number;
            taskId: number;
            projectId: number;
            repositoryId: number;
            repositoryName: string;
            externalId: string;
            issueNumber: number;
            title: string;
            url: string | null;
            status: string;
            stateReason: string | null;
            sourceUpdatedAt: string | null;
            lastAttemptAt: string | null;
            lastSuccessAt: string | null;
            lastError: string | null;
            trackingStatus: "WAITING" | "ACTIVE" | "ERROR" | "STOPPED";
            sourceVersion?: number;
            confirmationVersion?: number;
            taskVersion?: number;
            linkToken?: string;
            pending?: boolean;
            confirmedState?: components["schemas"]["IssueSourceState"];
            confirmedBy?: string | null;
            confirmedAt?: string | null;
        }>;
        list: (w: number, projectId?: number, page?: number, size?: number) => Promise<{
            items: components["schemas"]["TaskIssueSourceResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        listAll: (w: number, projectId?: number) => Promise<{
            id: number;
            taskId: number;
            projectId: number;
            repositoryId: number;
            repositoryName: string;
            externalId: string;
            issueNumber: number;
            title: string;
            url: string | null;
            status: string;
            stateReason: string | null;
            sourceUpdatedAt: string | null;
            lastAttemptAt: string | null;
            lastSuccessAt: string | null;
            lastError: string | null;
            trackingStatus: "WAITING" | "ACTIVE" | "ERROR" | "STOPPED";
            sourceVersion?: number;
            confirmationVersion?: number;
            taskVersion?: number;
            linkToken?: string;
            pending?: boolean;
            confirmedState?: components["schemas"]["IssueSourceState"];
            confirmedBy?: string | null;
            confirmedAt?: string | null;
        }[]>;
        create: (w: number, project: number, body: IssueTaskCreateRequest) => Promise<{
            task: components["schemas"]["TaskResponse"];
            created: boolean;
        }>;
        link: (w: number, project: number, body: IssueTaskLinkRequest) => Promise<{
            task: components["schemas"]["TaskResponse"];
            created: boolean;
        }>;
        unlink: (w: number, task: number) => Promise<null>;
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
            version?: number;
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
            version?: number;
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
            version?: number;
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
            version?: number;
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
            version?: number;
        }>;
        delete: (workspaceId: number, id: number) => Promise<null>;
    };
    noteApi: {
        revisions: (workspaceId: number, id: number, params?: NoteRevisionListParams) => Promise<{
            items: components["schemas"]["NoteRevisionSummaryResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
        revision: (workspaceId: number, id: number, revisionId: number) => Promise<{
            id: number;
            noteId: number;
            noteVersion: number;
            title: string;
            actorSubject: string | null;
            actorDisplayName: string | null;
            createdAt: string;
            source: components["schemas"]["NoteRevisionSource"];
            restoredFromRevisionId: number | null;
            content: string;
        }>;
        restoreRevision: (workspaceId: number, id: number, revisionId: number, expectedVersion: number) => Promise<{
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
        listDeleted: (workspaceId: number, params?: DeletedNoteListParams) => Promise<{
            items: components["schemas"]["DeletedNoteResponse"][];
            page: number;
            size: number;
            total: number;
            hasMore: boolean;
        }>;
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
        restore: (workspaceId: number, id: number) => Promise<{
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
        permanentlyDelete: (workspaceId: number, id: number) => Promise<null>;
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
