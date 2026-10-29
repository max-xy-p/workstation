import { createWorkstationApi } from '@workstation/api-client';
import { api, resetCsrfToken, submitWithCsrf } from './client';

export type {
  IssueSourceConfirmRequest,
  IssueSourceState,
  TaskIssueSourceResponse,
  IssueTaskCreateRequest,
  IssueTaskLinkRequest,
  GithubWebhookResponse,
  GithubWebhookRequest,
  GithubWebhookDeliveryResponse,
  GithubWebhookDeliveryPageResponse,
  IntegrationProvider,
  IntegrationScope,
  IntegrationConnectionRequest,
  IntegrationConnectionResponse,
  IntegrationTriggerRequest,
  IntegrationImportItemRequest,
  IntegrationImportRequest,
  RepositoryResponse,
  IntegrationRunResponse,
  IntegrationRunItemResponse,
  RepositoryBindRequest,
  IntegrationEnableRequest,
  DevelopmentKind,
  DevelopmentSnapshotResponse,
  DevelopmentItemResponse,
  ProjectDevelopmentResponse,
  RepositoryPageResponse,
  IntegrationConnectionPageResponse,
  IntegrationRunPageResponse,
  IntegrationRunItemPageResponse,
  DevelopmentItemPageResponse,
  ApiErrorResponse,
  AuditEventListParams,
  AuditEventPageResponse,
  DashboardResponse,
  DeletedNoteListParams,
  DeletedNotePageResponse,
  DeletedNoteResponse,
  NoteDetailResponse,
  NoteRevisionSource,
  NoteRevisionSummaryResponse,
  NoteRevisionResponse,
  NoteRevisionPageResponse,
  NoteRevisionRestoreRequest,
  NoteRevisionListParams,
  NoteFolderRequest,
  NoteFolderResponse,
  NoteImportRequest,
  NoteImportResponse,
  NoteListParams,
  NotePageResponse,
  NoteRequest,
  NoteUpdateRequest,
  NoteSummaryResponse,
  ProjectDetailResponse,
  ProjectListParams,
  ProjectPageResponse,
  ProjectSummaryResponse,
  ProjectRequest,
  ProjectResponse,
  ProjectStatus,
  TagRequest,
  TagResponse,
  TaskListParams,
  TaskPageResponse,
  TaskRequest,
  TaskUpdateRequest,
  TaskResponse,
  TaskStatus,
  WorkspacePermission,
  WorkspaceMemberCandidate,
  WorkspaceRequest,
  WorkspaceResponse,
  WorkspaceRole
} from '@workstation/api-client';

interface CurrentUserResponse {
  subjectId: string;
  username: string;
  displayName: string | null;
  principalType: 'USER' | 'SERVICE';
  systemRoles: string[];
}

// Gateway BFF 接口不属于业务 OpenAPI 契约，类型在浏览器适配层本地维护。
export const authApi = {
  currentUser: () => api.get<CurrentUserResponse>('/auth/me', undefined, {
    suppressUnauthorized: true
  }),
  login(registration = false): void {
    // 这里只发起 OIDC 登录；用户名和密码始终由 Keycloak 页面收集。
    window.location.assign(registration ? '/oauth2/authorization/keycloak?registration=true' : '/oauth2/authorization/keycloak');
  },
  async logout(): Promise<void> {
    await submitWithCsrf('/auth/logout');
    resetCsrfToken();
  }
};

const workstationApi = createWorkstationApi(api);

export const {
  issueApi,
  webhookApi,
  integrationApi,
  workspaceApi,
  auditApi,
  dashboardApi,
  projectApi,
  taskApi,
  noteApi,
  noteFolderApi,
  tagApi
} = workstationApi;
